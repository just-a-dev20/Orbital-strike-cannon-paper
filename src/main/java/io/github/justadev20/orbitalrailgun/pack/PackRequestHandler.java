package io.github.justadev20.orbitalrailgun.pack;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * First handler of every new connection on the game port. Minecraft connections never start with "GET " or
 * "HEAD" (they start with a VarInt length or 0xFE), so those are answered as HTTP requests for the pack;
 * everything else is passed on unchanged and the handler removes itself.
 */
final class PackRequestHandler extends ChannelInboundHandlerAdapter {
    private static final int MAX_REQUEST_BYTES = 8192;
    private static final ByteBuf HEADER_END = Unpooled.unreleasableBuffer(
            Unpooled.wrappedBuffer("\r\n\r\n".getBytes(StandardCharsets.US_ASCII)).asReadOnly());
    /** The path contains the pack's SHA-1, so a response for it never changes. */
    private static final String CACHE_FOUND = "public, max-age=31536000, immutable";

    private final Supplier<GamePortHost.@Nullable ServedPack> pack;
    private @Nullable ByteBuf received;

    PackRequestHandler(Supplier<GamePortHost.@Nullable ServedPack> pack) {
        this.pack = pack;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf data)) {
            ctx.fireChannelRead(msg);
            return;
        }
        if (received == null) {
            received = ctx.alloc().buffer();
        }
        received.writeBytes(data);
        data.release();
        if (received.readableBytes() < 4) {
            return;
        }
        String start = received.toString(received.readerIndex(), 4, StandardCharsets.US_ASCII);
        if (!start.equals("GET ") && !start.equals("HEAD")) {
            ByteBuf forward = received;
            received = null;
            ctx.fireChannelRead(forward);
            ctx.pipeline().remove(this);
            return;
        }
        int headerEnd = ByteBufUtil.indexOf(HEADER_END, received);
        if (headerEnd < 0) {
            if (received.readableBytes() > MAX_REQUEST_BYTES) {
                ctx.close();
            }
            return;
        }
        String requestLine = received.toString(StandardCharsets.US_ASCII).lines().findFirst().orElse("");
        respond(ctx, requestLine);
    }

    private void respond(ChannelHandlerContext ctx, String requestLine) {
        String[] parts = requestLine.split(" ");
        GamePortHost.ServedPack served = pack.get();
        boolean found = parts.length >= 2 && served != null && served.path().equals(parts[1]);
        boolean head = parts[0].equals("HEAD");
        byte[] body = found ? served.bytes() : new byte[0];
        String header = (found ? "HTTP/1.1 200 OK" : "HTTP/1.1 404 Not Found") + "\r\n"
                + "Content-Type: " + (found ? "application/zip" : "text/plain") + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: " + (found ? CACHE_FOUND : "no-cache") + "\r\n"
                + "Connection: close\r\n\r\n";
        ctx.write(Unpooled.wrappedBuffer(header.getBytes(StandardCharsets.US_ASCII)));
        ctx.writeAndFlush(head ? Unpooled.EMPTY_BUFFER : Unpooled.wrappedBuffer(body)).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (received != null) {
            received.release();
            received = null;
        }
    }
}
