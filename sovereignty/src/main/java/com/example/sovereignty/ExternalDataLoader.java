package com.example.sovereignty;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Читает JSON-файлы, которые кладут другие плагины в plugins/Sovereignty/exports/.
 *
 * Имя файла без расширения = имя поля в итоговом JSON.
 * Содержимое файла = сырое JSON-значение (объект, массив, число, строка).
 *
 * Пример:
 *   exports/jackpot.json     → {"jackpot": 1234567}       (но берётся только число 1234567)
 *   exports/bounties.json    → [{"target": "Steve", ...}] (весь массив)
 *
 * Никакого парсинга — данные вставляются как есть в WebPanelUploader.
 */
public class ExternalDataLoader {

    /** Директория с экспортами. Создаётся при старте. */
    public static final String EXPORTS_DIR_NAME = "exports";

    private final SovereigntyPlugin plugin;
    private final File exportsDir;

    public ExternalDataLoader(SovereigntyPlugin plugin) {
        this.plugin = plugin;
        this.exportsDir = new File(plugin.getDataFolder(), EXPORTS_DIR_NAME);
        if (!exportsDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            exportsDir.mkdirs();
        }
    }

    public File getExportsDir() {
        return exportsDir;
    }

    /**
     * Возвращает Map<fieldName, rawJsonValue>.
     * Если файл повреждён или пустой — пропускается.
     */
    public Map<String, String> loadAll() {
        Map<String, String> result = new HashMap<>();
        File[] files = exportsDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) return result;

        for (File file : files) {
            String fileName = file.getName();
            String fieldName = fileName.substring(0, fileName.length() - 5); // убрать ".json"

            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
                if (content.isEmpty()) continue;
                result.put(fieldName, content);
            } catch (IOException e) {
                plugin.getLogger().warning("[ExternalData] Ошибка чтения " + fileName + ": " + e.getMessage());
            }
        }

        return result;
    }
}
