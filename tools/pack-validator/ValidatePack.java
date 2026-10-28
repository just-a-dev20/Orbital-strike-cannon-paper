import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import net.minecraft.SharedConstants;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.cuboid.UnbakedCuboidGeometry;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;

/**
 * Parses the generated resource pack with the vanilla 26.3 client code, without a GPU.
 * ClientBootstrap is not called as a whole because it also initialises the Vulkan backend.
 */
public class ValidatePack {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ItemModels.bootstrap();
        SpecialModelRenderers.bootstrap();
        ItemTintSources.bootstrap();
        SelectItemModelProperties.bootstrap();
        ConditionalItemModelProperties.bootstrap();
        RangeSelectItemModelProperties.bootstrap();

        try (ZipFile zip = new ZipFile(args[0])) {
            var meta = JsonParser.parseReader(reader(zip, "pack.mcmeta")).getAsJsonObject().get("pack");
            var section = PackMetadataSection.forPackType(PackType.CLIENT_RESOURCES).codec().parse(JsonOps.INSTANCE, meta).getOrThrow();
            var current = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
            System.out.println("pack.mcmeta: supports " + section.supportedFormats() + ", client " + current
                    + ", compatible " + section.supportedFormats().isValueInRange(current));

            ClientItem.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader(zip, "assets/minecraft/items/crossbow.json"))).getOrThrow();
            System.out.println("items/crossbow.json: ok");

            CuboidModel model = CuboidModel.fromStream(reader(zip, "assets/orbital_railgun/models/item/orbital_railgun.json"));
            System.out.println("models/item/orbital_railgun.json: " + ((UnbakedCuboidGeometry) model.geometry()).elements().size() + " elements");

            var field = Class.forName("net.minecraft.client.gui.font.FontManager$FontDefinitionFile").getField("CODEC");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Codec<Object> fontCodec = (Codec<Object>) field.get(null);
            fontCodec.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader(zip, "assets/orbital_railgun/font/overlay.json"))).getOrThrow();
            System.out.println("font/overlay.json: ok");
        }
    }

    private static InputStreamReader reader(ZipFile zip, String name) throws Exception {
        return new InputStreamReader(zip.getInputStream(zip.getEntry(name)), StandardCharsets.UTF_8);
    }
}
