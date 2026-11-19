package dev.phantom.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Single-line text buffer used by search, profile names and string settings. */
public final class TextInput {
    public String text = "";
    public final int maxLength;
    public final String placeholder;
    public Consumer<String> onChange = s -> {
    };
    public Consumer<String> onSubmit = s -> {
    };

    public TextInput(int maxLength, String placeholder) {
        this.maxLength = maxLength;
        this.placeholder = placeholder;
    }

    public void set(String value) {
        text = value == null ? "" : value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    /** @return true if consumed */
    public boolean keyPressed(int key, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!text.isEmpty()) {
                    if (Screen.hasControlDown()) {
                        String t = text.stripTrailing();
                        int i = Math.max(t.lastIndexOf(' '), t.lastIndexOf(','));
                        text = i < 0 ? "" : t.substring(0, i + 1);
                    } else {
                        text = text.substring(0, text.length() - 1);
                    }
                    onChange.accept(text);
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                onSubmit.accept(text);
                return true;
            }
            case GLFW.GLFW_KEY_V -> {
                if (Screen.hasControlDown()) {
                    String clip = MinecraftClient.getInstance().keyboard.getClipboard().replaceAll("[\\r\\n\\t]", " ");
                    set(text + clip);
                    onChange.accept(text);
                    return true;
                }
                return false;
            }
            default -> {
                return key != GLFW.GLFW_KEY_ESCAPE;
            }
        }
    }

    public void charTyped(char c) {
        if (c < 32 || c == 127 || text.length() >= maxLength) return;
        text += c;
        onChange.accept(text);
    }
}
