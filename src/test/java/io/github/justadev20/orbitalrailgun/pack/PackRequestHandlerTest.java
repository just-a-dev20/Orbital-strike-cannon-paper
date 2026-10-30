package io.github.justadev20.orbitalrailgun.pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PackRequestHandlerTest {
    private static final String PATH = "/orbital_railgun/0123456789abcdef0123456789abcdef01234567.zip";
    private static final byte[] ZIP = {'P', 'K', 3, 4, 1, 2, 3, 4, 5};
    /** Handshake of a modern client: length, packet id 0, protocol 777, "localhost", port 25565, intent 2. */
    private static final byte[] HANDSHAKE = {16, 0, (byte) 0x89, 6, 9, 'l', 'o', 'c', 'a', 'l', 'h', 'o', 's', 't', 0x63, (byte) 0xDD, 2};

    private @Nullable EmbeddedChannel channel;

    private EmbeddedChannel channel(GamePortHost.@Nullable ServedPack pack) {
        channel = new EmbeddedChannel(new PackRequestHandler(() -> pack));
        return channel;
    }

    @AfterEach
    void close() {
        if (channel != null) {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void getServesThePack() {
        EmbeddedChannel channel = channel(new GamePortHost.ServedPack(PATH, ZIP));
        channel.writeInbound(ascii("GET " + PATH + " HTTP/1.1\r\nHost: example.org:25565\r\n\r\n"));

        Response response = response(channel);
        assertEquals("HTTP/1.1 200 OK", response.statusLine());
        assertTrue(response.header().contains("\r\nContent-Type: application/zip\r\n"));
        assertTrue(response.header().contains("\r\nContent-Length: " + ZIP.length + "\r\n"));
        assertTrue(response.header().contains("\r\nCache-Control: public, max-age=31536000, immutable\r\n"));
        assertTrue(response.header().contains("\r\nConnection: close\r\n"));
        assertArrayEquals(ZIP, response.body());
        assertFalse(channel.isOpen());
    }

    @Test
    void headSendsOnlyTheHeader() {
        EmbeddedChannel channel = channel(new GamePortHost.ServedPack(PATH, ZIP));
        channel.writeInbound(ascii("HEAD " + PATH + " HTTP/1.1\r\n\r\n"));

        Response response = response(channel);
        assertEquals("HTTP/1.1 200 OK", response.statusLine());
        assertTrue(response.header().contains("\r\nContent-Length: " + ZIP.length + "\r\n"));
        assertEquals(0, response.body().length);
        assertFalse(channel.isOpen());
    }

    @Test
    void unknownPathIsNotFound() {
        EmbeddedChannel channel = channel(new GamePortHost.ServedPack(PATH, ZIP));
        channel.writeInbound(ascii("GET /orbital_railgun/outdated.zip HTTP/1.1\r\n\r\n"));

        Response response = response(channel);
        assertEquals("HTTP/1.1 404 Not Found", response.statusLine());
        assertTrue(response.header().contains("\r\nContent-Length: 0\r\n"));
        assertTrue(response.header().contains("\r\nCache-Control: no-cache\r\n"));
        assertEquals(0, response.body().length);
    }

    @Test
    void nothingIsFoundWhileNoPackIsServed() {
        EmbeddedChannel channel = channel(null);
        channel.writeInbound(ascii("GET " + PATH + " HTTP/1.1\r\n\r\n"));

        assertEquals("HTTP/1.1 404 Not Found", response(channel).statusLine());
    }

    @Test
    void requestSplitAcrossReadsIsAnsweredOnceComplete() {
        EmbeddedChannel channel = channel(new GamePortHost.ServedPack(PATH, ZIP));
        channel.writeInbound(ascii("GE"));
        channel.writeInbound(ascii("T " + PATH + " HTTP/1.1\r\n"));
        channel.writeInbound(ascii("Host: example.org\r\n\r"));
        assertNull(channel.readOutbound());
        assertTrue(channel.isOpen());

        channel.writeInbound(ascii("\n"));
        assertArrayEquals(ZIP, response(channel).body());
    }

    @Test
    void minecraftTrafficIsPassedOnUnchanged() {
        EmbeddedChannel channel = channel(new GamePortHost.ServedPack(PATH, ZIP));
        channel.writeInbound(Unpooled.wrappedBuffer(HANDSHAKE));

        assertArrayEquals(HANDSHAKE, readInbound(channel));
        assertNull(channel.pipeline().get(PackRequestHandler.class));
        assertNull(channel.readOutbound());
        assertTrue(channel.isOpen());
    }

    @Test
    void minecraftTrafficShorterThanTheMethodIsCollectedFirst() {
        EmbeddedChannel channel = channel(new GamePortHost.ServedPack(PATH, ZIP));
        channel.writeInbound(Unpooled.wrappedBuffer(Arrays.copyOfRange(HANDSHAKE, 0, 2)));
        assertNull(channel.readInbound());

        channel.writeInbound(Unpooled.wrappedBuffer(Arrays.copyOfRange(HANDSHAKE, 2, HANDSHAKE.length)));
        assertArrayEquals(HANDSHAKE, readInbound(channel));
        assertNull(channel.pipeline().get(PackRequestHandler.class));
    }

    @Test
    void legacyPingIsPassedOn() {
        EmbeddedChannel channel = channel(new GamePortHost.ServedPack(PATH, ZIP));
        byte[] ping = {(byte) 0xFE, 0x01, (byte) 0xFA, 0x00, 0x0B};
        channel.writeInbound(Unpooled.wrappedBuffer(ping));

        assertArrayEquals(ping, readInbound(channel));
    }

    @Test
    void oversizedRequestIsClosedWithoutAnswer() {
        EmbeddedChannel channel = channel(new GamePortHost.ServedPack(PATH, ZIP));
        channel.writeInbound(ascii("GET " + PATH + " HTTP/1.1\r\n"));
        channel.writeInbound(ascii("X-Padding: " + "a".repeat(9000) + "\r\n"));

        assertFalse(channel.isOpen());
        assertNull(channel.readOutbound());
    }

    private static ByteBuf ascii(String text) {
        return Unpooled.copiedBuffer(text, StandardCharsets.US_ASCII);
    }

    private static byte[] readInbound(EmbeddedChannel channel) {
        ByteBuf buffer = channel.readInbound();
        try {
            return ByteBufUtil.getBytes(buffer);
        } finally {
            buffer.release();
        }
    }

    private static Response response(EmbeddedChannel channel) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (ByteBuf buffer; (buffer = channel.readOutbound()) != null; ) {
            out.writeBytes(ByteBufUtil.getBytes(buffer));
            buffer.release();
        }
        byte[] bytes = out.toByteArray();
        String text = new String(bytes, StandardCharsets.US_ASCII);
        int end = text.indexOf("\r\n\r\n");
        assertTrue(end > 0, "no complete response header: " + text);
        return new Response(text.substring(0, end + 4), Arrays.copyOfRange(bytes, end + 4, bytes.length));
    }

    private record Response(String header, byte[] body) {
        String statusLine() {
            return header.substring(0, header.indexOf("\r\n"));
        }
    }
}
