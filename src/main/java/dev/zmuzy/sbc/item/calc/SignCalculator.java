// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item.calc;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.market.BazaarSign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.text.NumberFormat;
import java.util.Locale;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyblocker SignCalculator + CalculatorCommand, with the sign checks from AbstractSignEditScreenMixin
// an expression typed in a number sign gets worked out as you type, the answer is what the sign sends
// screen hooks are in mc.mixin.SignInputMixin
public final class SignCalculator {
    public static final String ID = "sign_calculator";
    private static final String INPUT_SIGN_MARKER = "^^^^^^^^^^^^^^^", ALT_INPUT_SIGN_MARKER = "^^^^^^", BAZAAR_FLIP_MARKER = "^^Flipping^^", SET_NAME_MARKER = "Enter name";
    private static final NumberFormat DOUBLE_NUMBERS = doubleNumbers();

    private static String lastInput;
    private static double output;
    private static String error;

    private SignCalculator() {}

    private static NumberFormat doubleNumbers() {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setMaximumFractionDigits(2);
        return nf;
    }

    private static boolean option(String key, boolean def) {
        Feature f = Features.get(ID);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    public static boolean inSkyBlock() {
        return GameState.inSkyBlock || SbcMod.DEV;
    }

    // skyblocker isInputSign: a sign that wants a number, not a search or a name
    public static boolean isInputSign(String[] messages) {
        boolean inputSearch = messages[2].endsWith("your") || messages[2].endsWith("query");
        boolean setName = messages[2].equals(SET_NAME_MARKER);
        boolean altSearch = messages[2].endsWith("your");
        return messages[1].equals(INPUT_SIGN_MARKER) && !inputSearch
                || messages[1].equals(ALT_INPUT_SIGN_MARKER) && !altSearch && !setName
                || messages[1].equals(BAZAAR_FLIP_MARKER);
    }

    // line above the sign: "2k+5 = 2,005" or why it cant be worked out. nothing without the leading "=" when
    // thats asked for
    public static void draw(GuiGraphicsExtractor g, Font font, String message, int x, int y) {
        if (!Features.on(ID)) return;
        String text = line(message);
        if (text != null) g.centeredText(font, Component.literal(text), x, y, 0xFFFFFFFF);
    }

    // skyblocker extractCalculator minus the drawing, null when theres nothing to show
    static String line(String message) {
        if (option("requiresEquals", false) && !message.startsWith("=")) {
            output = -1;
            error = null;
            lastInput = message;
            return null;
        }
        if (message.startsWith("=")) message = message.substring(1);
        calculate(message);
        if (output == -1) return "§c" + (error != null ? error : "Invalid Equation");
        return "§a" + message + " = " + DOUBLE_NUMBERS.format(output);
    }

    // only recalcs when the expression changed
    public static void calculate(String message) {
        if (message.equals(lastInput)) return;
        lastInput = message;
        try {
            output = Calculator.calculate(message);
            error = null;
        } catch (Calculator.CalculatorException e) {
            output = -1;
            error = e.getMessage();
        }
    }

    // what the sign sends: a price keeps 2 decimals, an amount gets rounded. what you typed if it couldnt be worked out
    public static String newValue(boolean isPrice) {
        if (output == -1) return lastInput;
        if (isPrice) return String.valueOf(Math.round(output * 100d) / 100d);
        return Long.toString(Math.round(output));
    }

    // skyblocker finishEditing: first line becomes the answer, max 15 chars
    public static void finish(String[] messages) {
        if (!inSkyBlock() || !isInputSign(messages) || !Features.on(ID)) return;
        // render path might not have run for this text, like when a quick quantity button closed the sign
        line(messages[0]);
        if (lastInput == null) return;
        String value = newValue(messages[2].contains("price"));
        if (value.length() >= 15) value = value.substring(0, 15);
        messages[0] = value;
    }

    // skyblocker AbstractSignEditScreenMixin.keyPressed: enter closes the sign
    public static boolean closesOnEnter(String[] messages) {
        return inSkyBlock() && isInputSign(messages) && Features.on(ID) && option("closeSignsWithEnter", true);
    }

    private static int command(String calculation) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        mc.player.sendSystemMessage(Component.literal(result(calculation)));
        return 1;
    }

    // from skyblocker CalculatorCommand.doCalculation
    static String result(String calculation) {
        String text = "§b[SBC] §b" + calculation + " §r= ";
        try {
            return text + "§a" + DOUBLE_NUMBERS.format(Calculator.calculate(calculation));
        } catch (Calculator.CalculatorException e) {
            return "§b[SBC] §b" + calculation + " §r= §cInvalid Equation: " + e.getMessage();
        }
    }

    public static void register() {
        Features.add(new Feature(ID, "Items", "Sign calculator", "In the signs that ask for a number, write 2k+5, 3s (64 each), 1.5m/3 or purse/2: the answer shows above the sign and is what it sends. Also works as /calc.", "Skyblocker: sign calculator")
                .when(SignCalculator::inSkyBlock)
                .option(Option.bool("requiresEquals", "Only with an equals sign", "Only work it out when the sign starts with =.", false))
                .option(Option.bool("closeSignsWithEnter", "Enter closes the sign", "Enter sends a number sign instead of going to its next line.", true)));
        Safe.commands("SignCalculator.commands", (dispatcher, registry) -> {
            dispatcher.register(literal("calc").then(argument("equation", greedyString()).executes(c -> command(getString(c, "equation")))));
            dispatcher.register(literal("sbc").then(literal("calc").then(argument("equation", greedyString()).executes(c -> command(getString(c, "equation"))))));
        });
        dev.zmuzy.sbc.core.Commands.describe("calc", "Work an expression out: /calc 2k*3");
        BazaarSign.register();
    }
}
