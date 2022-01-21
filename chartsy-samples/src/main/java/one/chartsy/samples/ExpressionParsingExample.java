package one.chartsy.samples;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

public class ExpressionParsingExample {

    public static void main(String[] args) {
        ExpressionParser expressionParser = new SpelExpressionParser();
        Expression expression = expressionParser.parseExpression(
                "T(one.chartsy.data.provider.file.FlatFileFormat).STOOQ.newDataProvider('C:/Users/Mariusz/Downloads/d_pl_txt(9).zip')");
        expression = expressionParser.parseExpression(
                "T(one.chartsy.data.provider.file.FlatFileFormat).STOOQ.newDataProvider('C:/Users/Mariusz/Downloads/d_us_txt(2).zip')");
        System.out.println(((SpelExpression) expression).getAST());
        var result = expression.getValue();
        System.out.println(result);
        System.out.println(result.getClass());
    }
}
