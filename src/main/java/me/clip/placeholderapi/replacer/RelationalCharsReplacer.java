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

public final class RelationalCharsReplacer implements RelationalReplacer {

    @NotNull
    private final Closure closure;
    private final String prefix;

    public RelationalCharsReplacer(@NotNull final Closure closure) {
        this.closure = closure;
        this.prefix = closure.head + "rel_";
    }

    /**
     * Translates relational placeholders within the provided text using a
     * character-scanning approach.
     *
     * @param text   The raw text containing potential relational placeholders.
     * @param one    The first {@link Player} used for relational context.
     * @param two    The second {@link Player} used for relational context.
     * @param lookup A function that maps a lowercase identifier string to a registered
     *               {@link PlaceholderExpansion}.
     * @return A string with all valid relational placeholders replaced.
     */
    @NotNull
    @Override
    public String apply(@NotNull final String text, @Nullable final Player one, @Nullable final Player two,
                        @NotNull final Function<String, @Nullable PlaceholderExpansion> lookup) {
        int startPlaceholder = text.indexOf(prefix);

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

            final int contentStart = startPlaceholder + prefix.length();
            int underscoreIndex = -1;

            for (int i = contentStart; i < endPlaceholder; i++) {
                final char current = text.charAt(i);

                if (current == ' ' && underscoreIndex == -1) {
                    // Invalid placeholder (contains space before _).
                    // Treat the opening symbol as literal text and search for the next one.
                    builder.append(closure.head);
                    cursor = startPlaceholder + 1;
                    startPlaceholder = text.indexOf(prefix, cursor);

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
                startPlaceholder = text.indexOf(prefix, cursor);
                continue;
            }

            final String identifier = text.substring(contentStart, underscoreIndex);
            String parameters = "";

            if (underscoreIndex + 1 < endPlaceholder) {
                parameters = text.substring(underscoreIndex + 1, endPlaceholder);
            }

            final PlaceholderExpansion expansion = lookup.apply(identifier.toLowerCase(Locale.ROOT));
            String replacement = null;

            if (expansion instanceof Relational) {
                replacement = ((Relational) expansion).onPlaceholderRequest(one, two, parameters);
            }

            if (replacement != null) {
                builder.append(replacement);
            } else {
                // Fallback: Restore original placeholder format
                builder.append(text, startPlaceholder, endPlaceholder + 1);
            }

            cursor = endPlaceholder + 1;
            startPlaceholder = text.indexOf(prefix, cursor);

        } while (startPlaceholder != -1);

        if (cursor < length) {
            builder.append(text, cursor, length);
        }

        return builder.toString();
    }

}
