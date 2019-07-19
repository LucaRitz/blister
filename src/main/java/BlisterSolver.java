import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;
import org.ojalgo.type.context.NumberContext;

import java.util.List;
import java.util.Set;

public class BlisterSolver {

    void optimize(Set<BlisterData> data) {
        for (BlisterData blister : data) {
            final ExpressionsBasedModel model = new ExpressionsBasedModel();

            Expression allNewBlisterAreLowerOrEqualsMaximum = model.addExpression()
                    .upper(blister.newAmount);
            Expression allOldBlisterAreLowerOrEqualsMaximum = model.addExpression()
                    .upper(blister.oldAmount);

            for(Order order : blister.orders) {

                order.newAmount = model.addVariable()
                        .weight(1)
                        .integer(true)
                        .lower((int)Math.ceil((order.longtime/100.0d)*order.requestedAmount));
                order.oldAmount = model.addVariable()
                        .weight(2)
                        .integer(true)
                        .lower(0);

                model.addExpression()
                        .set(order.newAmount, 1)
                        .set(order.oldAmount, 1)
                        .upper(order.requestedAmount)
                        .lower(order.requestedAmount);

                allNewBlisterAreLowerOrEqualsMaximum
                        .set(order.newAmount, 1);
                allOldBlisterAreLowerOrEqualsMaximum
                        .set(order.oldAmount, 1);
            }

            Optimisation.Result result = model.maximise();

            System.out.println(result);

            if (!model.validate(new NumberContext())) {
                throw new IllegalArgumentException("ERROR: Cannot optimize");
            }
        }
    }

    public static class Order {
        private final int requestedAmount;
        private final long boar;
        private final long longtime;

        Variable newAmount;
        Variable oldAmount;

        public Order(int requestedAmount, long boar, long longtime) {
            this.requestedAmount = requestedAmount;
            this.boar = boar;
            this.longtime = longtime;
        }
    }

    static class BlisterData {
        private final int newAmount;
        private final int oldAmount;
        private final List<Order> orders;

        BlisterData(int newAmount, int oldAmount, List<Order> orders) {
            this.newAmount = newAmount;
            this.oldAmount = oldAmount;
            this.orders = orders;
        }
    }
}
