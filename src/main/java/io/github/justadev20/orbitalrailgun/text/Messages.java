package io.github.justadev20.orbitalrailgun.text;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;

/**
 * Builds translatable components. Clients with the resource pack translate the keys themselves,
 * everybody else sees the fallback text from the configured language.
 */
public final class Messages {
    public static final List<String> LANGUAGES = List.of("ar_sa", "de_de", "en_us", "es_es", "fr_fr", "hi_in", "it_it",
            "ja_jp", "ko_kr", "nl_nl", "pl_pl", "pt_br", "ru_ru", "sv_se", "zh_cn");

    private final Map<String, String> fallback;

    private Messages(Map<String, String> fallback) {
        this.fallback = fallback;
    }

    public static Messages load(String language, Logger logger) {
        Map<String, String> texts = new HashMap<>(read("en_us"));
        if (!LANGUAGES.contains(language)) {
            logger.warning("Unknown language '" + language + "', falling back to en_us. Available: " + LANGUAGES);
        } else {
            texts.putAll(read(language));
        }
        return new Messages(Map.copyOf(texts));
    }

    public Component tr(String key, Object... args) {
        ComponentLike[] components = new ComponentLike[args.length];
        for (int i = 0; i < args.length; i++) {
            components[i] = args[i] instanceof ComponentLike like ? like : Component.text(String.valueOf(args[i]));
        }
        return Component.translatable(key, fallback.getOrDefault(key, key), components);
    }

    private static Map<String, String> read(String language) {
        try (InputStream in = Messages.class.getResourceAsStream("/lang/" + language + ".json")) {
            if (in == null) {
                return Map.of();
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> texts = new HashMap<>();
            json.entrySet().forEach(entry -> texts.put(entry.getKey(), entry.getValue().getAsString()));
            return texts;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read bundled language " + language, e);
        }
    }
}
