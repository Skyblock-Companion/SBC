// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.calc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyblocker Calculator: lexes an expression (s = 64, e = 160, k, m, b, t, q, x for times, p or purse =
// your coins, implicit times before brackets and functions)
// then shunting yard into rpn and evaluates it. messages are its own english lang lines
public final class Calculator {
    private enum TokenType { NUMBER, OPERATOR, FUNCTION, L_PARENTHESIS, R_PARENTHESIS }

    private record Token(TokenType type, Object value) {}

    private enum Operator {
        ADD("+"), SUB("-"), MULT("*"), DIV("/"), MOD("%"), POW("^", true);

        final String op;
        final boolean rightAssociative;

        Operator(String op) {
            this(op, false);
        }

        Operator(String op, boolean rightAssociative) {
            this.op = op;
            this.rightAssociative = rightAssociative;
        }

        static Operator of(String s) {
            for (Operator o : values()) if (o.op.equals(s)) return o;
            return null;
        }
    }

    @FunctionalInterface
    private interface Fn {
        double apply(double val) throws CalculatorException;
    }

    private enum Function {
        SQRT("sqrt", val -> {
            if (val < 0) throw brokeMath(val, "sqrt");
            return Math.sqrt(val);
        }),
        LOG("log", val -> {
            if (val <= 0) throw brokeMath(val, "log");
            return Math.log10(val);
        }),
        LG("lg", val -> {
            if (val <= 0) throw brokeMath(val, "lg");
            return Math.log(val) / Math.log(2);
        }),
        LN("ln", val -> {
            if (val <= 0) throw brokeMath(val, "ln");
            return Math.log(val);
        }),
        FACTORIAL("factorial", val -> {
            if (val < 0 || val > 170) throw brokeMath(val, "factorial");
            double result = 1;
            for (int i = 2; i <= (int) val; i++) result *= i;
            return result;
        }),
        SIN("sin", val -> Math.sin(Math.toRadians(val))),
        COS("cos", val -> Math.cos(Math.toRadians(val))),
        TAN("tan", val -> Math.tan(Math.toRadians(val))),
        ASIN("asin", val -> {
            if (val < -1 || val > 1) throw brokeMath(val, "asin");
            return Math.toDegrees(Math.asin(val));
        }),
        ACOS("acos", val -> {
            if (val < -1 || val > 1) throw brokeMath(val, "acos");
            return Math.toDegrees(Math.acos(val));
        }),
        ATAN("atan", val -> Math.toDegrees(Math.atan(val))),
        SINH("sinh", Math::sinh),
        COSH("cosh", Math::cosh),
        TANH("tanh", val -> {
            if (val > 20) return 1.0;
            if (val < -20) return -1.0;
            return Math.tanh(val);
        }),
        ABS("abs", Math::abs),
        FLOOR("floor", Math::floor),
        CEIL("ceil", Math::ceil),
        ROUND("round", Math::round);

        final String name;
        final Fn function;

        Function(String name, Fn function) {
            this.name = name;
            this.function = function;
        }

        static Function of(String s) {
            for (Function f : values()) if (f.name.equals(s)) return f;
            return null;
        }
    }

    private static final Pattern NUMBER_PATTERN = Pattern.compile("([_,\\d]+\\.?[_,\\d]*)([sekmbtq]?)");
    private static final Map<String, Long> MAGNITUDE_VALUES = Map.of(
            "s", 64L,
            "e", 160L,
            "k", 1_000L,
            "m", 1_000_000L,
            "b", 1_000_000_000L,
            "t", 1_000_000_000_000L,
            "q", 1_000_000_000_000_000L);

    // skyblocker Utils.getPurse, swappable in tests
    public static LongSupplier purse = () -> Math.max(0, io.muzy.sbc.core.GameState.purse);

    private Calculator() {}

    private static CalculatorException brokeMath(double val, String function) {
        return new CalculatorException("Invalid input " + val + " for function '" + function + "'");
    }

    private static List<Token> lex(String text) throws CalculatorException {
        List<Token> tokens = new ArrayList<>();
        String input = text.replace(" ", "").toLowerCase(Locale.ENGLISH).replace("x", "*");
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            Token next;
            switch (c) {
                case '+', '-', '*', '/', '%', '^' -> {
                    Operator operator = Operator.of(String.valueOf(c));
                    // no double operators like "5 ++ 2"
                    if (!tokens.isEmpty() && tokens.getLast().type() == TokenType.OPERATOR) throw new CalculatorException("Unexpected duplicate operator");
                    next = new Token(TokenType.OPERATOR, operator);
                }
                case '(' -> {
                    // implicit times when theres a number before brackets
                    if (!tokens.isEmpty()) {
                        TokenType lastType = tokens.getLast().type();
                        if (lastType == TokenType.R_PARENTHESIS || lastType == TokenType.NUMBER) tokens.add(new Token(TokenType.OPERATOR, Operator.MULT));
                    }
                    next = new Token(TokenType.L_PARENTHESIS, "(");
                }
                case ')' -> next = new Token(TokenType.R_PARENTHESIS, ")");
                case '_', ',' -> throw new CalculatorException("Number literals cannot start with a separator");
                case '.' -> throw new CalculatorException("Number literals cannot contain a leading decimal point");
                case '1', '2', '3', '4', '5', '6', '7', '8', '9', '0' -> {
                    Matcher numberMatcher = NUMBER_PATTERN.matcher(input.substring(i));
                    if (!numberMatcher.find()) throw new CalculatorException("Invalid character '" + input.substring(i) + "'");
                    int end = numberMatcher.end();
                    String number = input.substring(i, i + end);
                    i += end - 1;
                    next = new Token(TokenType.NUMBER, number);
                }
                case 'p' -> {
                    if (!tokens.isEmpty() && tokens.getLast().type() == TokenType.NUMBER) throw new CalculatorException("Invalid Equation");
                    if (input.substring(i, Math.min(input.length(), i + 5)).equals("purse")) i += 4;
                    next = new Token(TokenType.NUMBER, String.valueOf(purse.getAsLong()));
                }
                default -> {
                    String func = input.substring(i).split("[ (]", 2)[0];
                    Function function = Function.of(func);
                    if (function == null) throw new CalculatorException("Invalid operator '" + func + "'");
                    // implicit times when theres a number before functions
                    if (!tokens.isEmpty()) {
                        TokenType lastType = tokens.getLast().type();
                        if (lastType == TokenType.R_PARENTHESIS || lastType == TokenType.NUMBER) tokens.add(new Token(TokenType.OPERATOR, Operator.MULT));
                    }
                    i += func.length() - 1;
                    next = new Token(TokenType.FUNCTION, function);
                }
            }
            tokens.add(next);
            i++;
        }
        return tokens;
    }

    // shunting yard: infix -> rpn
    private static List<Token> shunt(List<Token> tokens) throws CalculatorException {
        Deque<Token> operatorStack = new ArrayDeque<>();
        List<Token> outputQueue = new ArrayList<>();
        for (Token shuntingToken : tokens) {
            switch (shuntingToken.type()) {
                case NUMBER -> outputQueue.add(shuntingToken);
                case OPERATOR -> {
                    Operator op = (Operator) shuntingToken.value();
                    int precedence = precedence(op);
                    while (!operatorStack.isEmpty()) {
                        Token leftToken = operatorStack.peek();
                        if (leftToken.type() == TokenType.L_PARENTHESIS) break;
                        // skyblocker asserts an operator here, a function left on the stack is what that assert would catch
                        if (leftToken.type() != TokenType.OPERATOR) break;
                        int leftPrecedence = precedence((Operator) leftToken.value());
                        if (leftPrecedence > precedence || (leftPrecedence == precedence && !op.rightAssociative)) {
                            outputQueue.add(operatorStack.pop());
                            continue;
                        }
                        break;
                    }
                    operatorStack.push(shuntingToken);
                }
                case FUNCTION, L_PARENTHESIS -> operatorStack.push(shuntingToken);
                case R_PARENTHESIS -> {
                    while (true) {
                        if (operatorStack.isEmpty()) throw new CalculatorException("Unbalanced left parenthesis");
                        Token leftToken = operatorStack.pop();
                        if (leftToken.type() == TokenType.L_PARENTHESIS) break;
                        outputQueue.add(leftToken);
                    }
                    if (!operatorStack.isEmpty() && operatorStack.peek().type() == TokenType.FUNCTION) outputQueue.add(operatorStack.pop());
                }
            }
        }
        // empty the operator stack
        while (!operatorStack.isEmpty()) {
            Token leftToken = operatorStack.pop();
            // technically unbalanced left paren but just assume they close after the equation and skip them from here
            if (leftToken.type() == TokenType.L_PARENTHESIS) continue;
            outputQueue.add(leftToken);
        }
        return outputQueue;
    }

    private static int precedence(Operator operator) {
        return switch (operator) {
            case ADD, SUB -> 0;
            case MULT, DIV, MOD -> 1;
            case POW -> 2;
        };
    }

    private static double evaluate(List<Token> tokens) throws CalculatorException {
        Deque<Double> values = new ArrayDeque<>();
        for (Token token : tokens) {
            switch (token.type()) {
                case NUMBER -> values.push(calculateValue((String) token.value()));
                case OPERATOR -> {
                    if (values.size() < 2) throw new CalculatorException("Missing value around operator");
                    double right = values.pollFirst();
                    double left = values.pollFirst();
                    values.push(switch ((Operator) token.value()) {
                        case ADD -> left + right;
                        case SUB -> left - right;
                        case MULT -> left * right;
                        case DIV -> {
                            if (right == 0) throw new CalculatorException("Cannot divide " + left + " by 0");
                            yield left / right;
                        }
                        case MOD -> {
                            if (right == 0) throw new CalculatorException("Cannot modulo " + left + " by 0");
                            yield left % right;
                        }
                        case POW -> Math.pow(left, right);
                    });
                }
                case FUNCTION -> {
                    if (values.isEmpty()) throw new CalculatorException("Missing value around operator");
                    values.push(((Function) token.value()).function.apply(values.pollFirst()));
                }
                case L_PARENTHESIS, R_PARENTHESIS -> throw new CalculatorException("Unbalanced left parenthesis");
            }
        }
        if (values.isEmpty()) throw new CalculatorException("Equation is empty");
        return values.pop();
    }

    private static double calculateValue(String value) throws CalculatorException {
        Matcher numberMatcher = NUMBER_PATTERN.matcher(value.toLowerCase(Locale.ENGLISH));
        if (!numberMatcher.matches()) throw new CalculatorException("Invalid number '" + value.toLowerCase(Locale.ENGLISH) + "'");
        double number = Double.parseDouble(numberMatcher.group(1).replaceAll("[_,]", ""));
        String magnitude = numberMatcher.group(2);
        if (!magnitude.isEmpty()) {
            Long m = MAGNITUDE_VALUES.get(magnitude);
            if (m == null) throw new CalculatorException("Invalid magnitude '" + magnitude + "'");
            number *= m;
        }
        return number;
    }

    public static double calculate(String equation) throws CalculatorException {
        if (equation.startsWith(".")) equation = "0" + equation;
        return evaluate(shunt(lex(equation)));
    }

    public static final class CalculatorException extends Exception {
        public CalculatorException(String message) {
            super(message);
        }
    }
}
