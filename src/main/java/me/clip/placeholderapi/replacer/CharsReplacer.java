/*
 * This file is part of PlaceholderAPI
 *
 * PlaceholderAPI
 * Copyright (c) 2015 - 2026 PlaceholderAPI Team
 *
 * PlaceholderAPI free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PlaceholderAPI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package me.clip.placeholderapi.replacer;

import java.util.Locale;
import java.util.function.Function;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CharsReplacer implements Replacer {

    private static final String EMPTY_PREFIX = "";
    private static final String RELATIONAL_PREFIX = "rel_";

    @NotNull
    private final Closure closure;

    public CharsReplacer(@NotNull final Closure closure) {
        this.closure = closure;
    }

    /**
     * Translates placeholders within the provided text using a high-performance
     * character-scanning approach.
     * * <p>The method identifies placeholders delimited by the defined {@link Closure}
     * (e.g., %identifier_params% or {identifier_params}). If a placeholder is
     * successfully identified, the provided lookup function is used to fetch the
     * corresponding {@link PlaceholderExpansion}.</p>
     *
     * @param text   The raw text containing potential placeholders to be replaced.
     * @param player The {@link OfflinePlayer} to contextually parse the placeholders against.
     * May be {@code null} if no player context is available.
     * @param lookup A function that maps a lowercase identifier string to a registered
     * {@link PlaceholderExpansion}.
     * @return A string with all valid placeholders replaced by their respective values.
     * Returns the original text if no placeholders are found.
     */
    @NotNull
    @Override
    public String apply(@NotNull final String text, @Nullable final OfflinePlayer player,
                        @NotNull final Function<String, @Nullable PlaceholderExpansion> lookup) {
        return apply(text, EMPTY_PREFIX, lookup, (expansion, parameters) -> {
            if (expansion == null) {
                return null;
            }

            return expansion.onRequest(player, parameters);
        });
    }

    /**
     * Translates relational placeholders within the provided text using the same
     * character-scanning approach as normal placeholders.
     *
     * @param text   The raw text containing potential relational placeholders.
     * @param one    The first {@link Player} used for relational context.
     * @param two    The second {@link Player} used for relational context.
     * @param lookup A function that maps a lowercase identifier string to a registered
     *               {@link PlaceholderExpansion}.
     * @return A string with all valid relational placeholders replaced.
     */
    @NotNull
    public String applyRelational(@NotNull final String text, @Nullable final Player one, @Nullable final Player two,
                                  @NotNull final Function<String, @Nullable PlaceholderExpansion> lookup) {
        return apply(text, RELATIONAL_PREFIX, lookup, (expansion, parameters) -> {
            if (!(expansion instanceof Relational)) {
                return null;
            }

            return ((Relational) expansion).onPlaceholderRequest(one, two, parameters);
        });
    }

    @NotNull
    private String apply(@NotNull final String text, @NotNull final String placeholderPrefix,
                         @NotNull final Function<String, @Nullable PlaceholderExpansion> lookup,
                         @NotNull final PlaceholderHandler handler) {
        final char head = closure.head;
        final String placeholderStart = placeholderPrefix.isEmpty()
                ? null
                : head + placeholderPrefix;
        int startPlaceholder = findStart(text, head, placeholderStart, 0);

        if (startPlaceholder == -1) {
            return text;
        }

        final int length = text.length();
        final StringBuilder builder = new StringBuilder(length + (length >> 3));
        int cursor = 0;

        final char tail = closure.tail;

        loop: do {
            // Append plain text preceding the placeholder
            if (startPlaceholder > cursor) {
                builder.append(text, cursor, startPlaceholder);
            }

            final int endPlaceholder = text.indexOf(tail, startPlaceholder + 1);

            if (endPlaceholder == -1) {
                builder.append(text, startPlaceholder, length);
                return builder.toString();
            }

            final int contentStart = startPlaceholder + 1 + placeholderPrefix.length();
            int underscoreIndex = -1;

            for (int i = contentStart; i < endPlaceholder; i++) {
                final char current = text.charAt(i);

                if (current == ' ' && underscoreIndex == -1) {
                    // Invalid placeholder (contains space before _).
                    // Treat the opening symbol as literal text and search for the next one.
                    builder.append(head);
                    cursor = startPlaceholder + 1;
                    startPlaceholder = findStart(text, head, placeholderStart, cursor);

                    // Safety check: If no more placeholders exist, break to finalize
                    if (startPlaceholder == -1) {
                        break loop;
                    }
                    continue loop;
                }

                if (current == '_' && underscoreIndex == -1) {
                    underscoreIndex = i;
                }
            }

            if (underscoreIndex <= contentStart) {
                builder.append(text, startPlaceholder, endPlaceholder + 1);
                cursor = endPlaceholder + 1;
                startPlaceholder = findStart(text, head, placeholderStart, cursor);
                continue;
            }

            String identifier = text.substring(contentStart, underscoreIndex);
            String parameters = "";

            if (underscoreIndex + 1 < endPlaceholder) {
                parameters = text.substring(underscoreIndex + 1, endPlaceholder);
            }

            final PlaceholderExpansion expansion = lookup.apply(identifier.toLowerCase(Locale.ROOT));
            final String replacement = handler.apply(expansion, parameters);

            if (replacement != null) {
                builder.append(replacement);
            } else {
                builder.append(text, startPlaceholder, endPlaceholder + 1);
            }

            cursor = endPlaceholder + 1;
            startPlaceholder = findStart(text, head, placeholderStart, cursor);

        } while (startPlaceholder != -1);

        if (cursor < length) {
            builder.append(text, cursor, length);
        }

        return builder.toString();
    }

    private int findStart(@NotNull final String text, final char head,
                          @Nullable final String placeholderStart, final int fromIndex) {
        if (placeholderStart == null) {
            return text.indexOf(head, fromIndex);
        }

        return text.indexOf(placeholderStart, fromIndex);
    }

    private interface PlaceholderHandler {

        @Nullable
        String apply(@Nullable final PlaceholderExpansion expansion,
                     @NotNull final String parameters);
    }

}
