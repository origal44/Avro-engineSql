package com.avroengine.parser;

import com.avroengine.ast.BinaryExpr;
import com.avroengine.ast.BinaryOp;
import com.avroengine.ast.ColumnRef;
import com.avroengine.ast.Expr;
import com.avroengine.ast.IsNullExpr;
import com.avroengine.ast.Literal;
import com.avroengine.ast.SelectItem;
import com.avroengine.ast.SelectStatement;
import com.avroengine.ast.UnaryExpr;
import com.avroengine.ast.UnaryOp;
import com.avroengine.lexer.Lexer;
import com.avroengine.lexer.Token;
import com.avroengine.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

import static com.avroengine.lexer.TokenType.*;

/**
 * Handwritten recursive-descent parser. Boolean/comparison levels are plain
 * recursive descent; arithmetic levels use precedence climbing (a Pratt-style
 * left-associative loop) for {@code +,-} and {@code *,/}.
 *
 * <p>Precedence, low to high: OR &lt; AND &lt; NOT &lt; comparison (incl. IS
 * [NOT] NULL) &lt; +,- &lt; *,/ &lt; unary minus / primary.
 */
public final class Parser {

    private final List<Token> tokens;
    private final String source;
    private int pos = 0;
    private int prevEnd = 0;

    private Parser(List<Token> tokens, String source) {
        this.tokens = tokens;
        this.source = source;
    }

    public static SelectStatement parse(String sql) {
        Parser parser = new Parser(Lexer.tokenize(sql), sql);
        SelectStatement stmt = parser.parseSelectStatement();
        parser.expect(EOF, "Unexpected trailing input");
        return stmt;
    }

    private SelectStatement parseSelectStatement() {
        expect(SELECT, "Expected SELECT");

        boolean selectAll = false;
        List<SelectItem> items = new ArrayList<>();
        if (check(STAR)) {
            advance();
            selectAll = true;
        } else {
            items.add(parseSelectItem());
            while (check(COMMA)) {
                advance();
                items.add(parseSelectItem());
            }
        }

        expect(FROM, "Expected FROM");
        Token tableToken = expect(IDENTIFIER, "Expected table name");
        String tableName = tableToken.lexeme();

        Expr where = null;
        if (check(WHERE)) {
            advance();
            where = parseOr();
        }

        return new SelectStatement(selectAll, items, tableName, where);
    }

    private SelectItem parseSelectItem() {
        int start = current().position();
        Expr expr = parseOr();
        String display = source.substring(start, prevEnd).trim();
        return new SelectItem(expr, display);
    }

    // OR < AND < NOT < comparison < +,- < *,/ < unary/primary

    private Expr parseOr() {
        Expr left = parseAnd();
        while (check(OR)) {
            advance();
            Expr right = parseAnd();
            left = new BinaryExpr(left, BinaryOp.OR, right);
        }
        return left;
    }

    private Expr parseAnd() {
        Expr left = parseNot();
        while (check(AND)) {
            advance();
            Expr right = parseNot();
            left = new BinaryExpr(left, BinaryOp.AND, right);
        }
        return left;
    }

    private Expr parseNot() {
        if (check(NOT)) {
            advance();
            return new UnaryExpr(UnaryOp.NOT, parseNot());
        }
        return parseComparison();
    }

    private Expr parseComparison() {
        Expr left = parseAdditive();
        left = applyIsNullSuffix(left);

        TokenType t = current().type();
        BinaryOp op = switch (t) {
            case EQ -> BinaryOp.EQ;
            case NEQ -> BinaryOp.NEQ;
            case LT -> BinaryOp.LT;
            case LTE -> BinaryOp.LTE;
            case GT -> BinaryOp.GT;
            case GTE -> BinaryOp.GTE;
            default -> null;
        };
        if (op != null) {
            advance();
            Expr right = parseAdditive();
            left = new BinaryExpr(left, op, right);
        }
        return left;
    }

    private Expr applyIsNullSuffix(Expr operand) {
        if (check(IS)) {
            advance();
            boolean negated = false;
            if (check(NOT)) {
                advance();
                negated = true;
            }
            expect(NULL, "Expected NULL after IS" + (negated ? " NOT" : ""));
            return new IsNullExpr(operand, negated);
        }
        return operand;
    }

    private Expr parseAdditive() {
        Expr left = parseMultiplicative();
        while (check(PLUS) || check(MINUS)) {
            BinaryOp op = check(PLUS) ? BinaryOp.PLUS : BinaryOp.MINUS;
            advance();
            Expr right = parseMultiplicative();
            left = new BinaryExpr(left, op, right);
        }
        return left;
    }

    private Expr parseMultiplicative() {
        Expr left = parseUnary();
        while (check(STAR) || check(SLASH)) {
            BinaryOp op = check(STAR) ? BinaryOp.MUL : BinaryOp.DIV;
            advance();
            Expr right = parseUnary();
            left = new BinaryExpr(left, op, right);
        }
        return left;
    }

    private Expr parseUnary() {
        if (check(MINUS)) {
            advance();
            return new UnaryExpr(UnaryOp.NEG, parseUnary());
        }
        return parsePrimary();
    }

    private Expr parsePrimary() {
        Token tok = current();
        switch (tok.type()) {
            case INTEGER_LITERAL -> {
                advance();
                return new Literal(Long.parseLong(tok.lexeme()));
            }
            case DECIMAL_LITERAL -> {
                advance();
                return new Literal(Double.parseDouble(tok.lexeme()));
            }
            case STRING_LITERAL -> {
                advance();
                return new Literal(unescapeString(tok.lexeme()));
            }
            case TRUE -> {
                advance();
                return new Literal(Boolean.TRUE);
            }
            case FALSE -> {
                advance();
                return new Literal(Boolean.FALSE);
            }
            case NULL -> {
                advance();
                return new Literal(null);
            }
            case IDENTIFIER -> {
                advance();
                return new ColumnRef(tok.lexeme());
            }
            case LPAREN -> {
                advance();
                Expr inner = parseOr();
                expect(RPAREN, "Expected ')'");
                return inner;
            }
            default -> throw new ParseException("Unexpected token '" + tok.lexeme() + "'", tok.position());
        }
    }

    private static String unescapeString(String lexeme) {
        // lexeme is 'quoted, with ''-escapes' inclusive of outer quotes.
        String inner = lexeme.substring(1, lexeme.length() - 1);
        return inner.replace("''", "'");
    }

    // --- token stream helpers ---

    private Token current() {
        return tokens.get(pos);
    }

    private boolean check(TokenType type) {
        return current().type() == type;
    }

    private Token advance() {
        Token tok = tokens.get(pos);
        if (tok.type() != EOF) {
            pos++;
        }
        prevEnd = tok.position() + tok.lexeme().length();
        return tok;
    }

    private Token expect(TokenType type, String message) {
        if (!check(type)) {
            Token tok = current();
            throw new ParseException(message + ", found '" + tok.lexeme() + "'", tok.position());
        }
        return advance();
    }
}
