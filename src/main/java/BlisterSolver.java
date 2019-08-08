import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;

public class BlisterSolver {

    private static final int BIG_INT = 1_000_000;
    private static final double HUNDRED = 100.0d;
    private static final int FULFILLMENT_WEIGHT = 10_000;
    private static final int SHORTTIME_WEIGHT = 1;
    private static final int LONGTIME_WEIGHT = 1;

    /**
     * Optimiert die Verteilung der Blister auf die Bestellungen.
     * Wenn der Mindestlangzeitanteil nicht
     * gesetzt ist, so sollten möglichst viele neue Blister verkauft werden. Wird der Mindestlangzeitanteil gesetzt, so
     * sollen möglichst viele alte Blister verkauft werden.
     * <p>
     * Für genaue Beschreibung siehe:
     * UC 3.04.01 Bestellung automatisch erfüllen / Dokument Optimierung_der_Blister.docx
     *
     * <p>
     * Die zu optimierenden Variablen finden sich in {@link BlisterSolver.Order} und heissen
     * longtime und enabled.
     *
     * @param data Zu optimierende Daten
     */
    public void optimize(Collection<BlisterData> data) {
        data.forEach(this::optimize);
    }

    private void optimize(BlisterData blisterData) {
        final ExpressionsBasedModel model = new ExpressionsBasedModel();

        // Object function
        Expression orderFulfillment = model.addExpression("object_enabled")
                .weight(FULFILLMENT_WEIGHT);
        Expression shortTimeFulfillment = model.addExpression("object_shortTime")
                .weight(SHORTTIME_WEIGHT);
        Expression longTimeFulfillment = model.addExpression("object_longTime")
                .weight(LONGTIME_WEIGHT);

        // n_1 + ,..., + n_n <= N
        Expression allNewBlisterAreLowerOrEqualsMaximum = model.addExpression("newBlistersMaximum")
                .upper(BigInteger.valueOf(blisterData.newAmount));
        // a_1 + ,..., + a_n <= A
        Expression allOldBlisterAreLowerOrEqualsMaximum = model.addExpression("oldBlistersMaximum")
                .upper(BigInteger.valueOf(blisterData.oldAmount));
        int index = 0;

        List<Order> sortedByDate = new ArrayList<>(blisterData.orders);
        sortedByDate.sort(Comparator.comparing(Order::getOrderingDate));

        for (Order order : sortedByDate) {
            Variable enabled = model.addVariable("enabled_" + index)
                    .binary();
            Variable longtime = model.addVariable("longtime_" + index)
                    .lower(BigInteger.valueOf(0));
            Variable newAmount = model.addVariable("newAmount_" + index)
                    .integer(true);

            BigDecimal longTimeWeight;
            BigDecimal shortTimeWeight;
            BigDecimal lowerBound;
            if (order.getLongtimeValue() != null) {
                lowerBound = BigDecimal.valueOf(order.getLongtimeValue())
                        .divide(BigDecimal.valueOf(HUNDRED), 100, RoundingMode.HALF_EVEN);
                longTimeWeight = BigDecimal.valueOf(0);
                shortTimeWeight = BigDecimal.valueOf(1);
            } else {
                lowerBound = BigDecimal.valueOf(0);
                longTimeWeight = BigDecimal.valueOf(1);
                shortTimeWeight = BigDecimal.valueOf(0);
            }

            order.setLongtime(longtime);
            order.setEnabled(enabled);

            // enabled = 1 -> longtime >= 0
            // enabled = 0 -> longtime = 0
            bindLongtimeToEnabled(model, index, enabled, longtime, lowerBound);

            // newAmount = (longtime * requested)
            // (longtime * requested) - newAmount = 0
            // Resultat muss ganzzahlig sein -> Siehe newAmount
            bindNewAmountToLongtime(model, index, newAmount, longtime, order.getRequestedAmount());

            Variable oldAmount = model.addVariable("oldAmount_" + index)
                    .integer(true);
            // oldAmount + newAmount = requested
            bindOldAmountToNewAmount(model, index, newAmount, oldAmount, order.getRequestedAmount());

            Variable oldDeduction = model.addVariable("oldDeduction_" + index);
            // oldDeduction = enabled * oldValue
            bindOldDeductionToEnabledAndOldValue(model, index, enabled, oldAmount, oldDeduction);

            allNewBlisterAreLowerOrEqualsMaximum
                    .set(newAmount, BigDecimal.valueOf(1));
            allOldBlisterAreLowerOrEqualsMaximum
                    .set(oldDeduction, BigDecimal.valueOf(1));

            // Update Object-function
            Variable constantOne = model.addVariable("constant_one_" + index)
                    .integer(true)
                    .level(1);
            int divider = (index + 1);
            int reversedDivider = sortedByDate.size() + 1 - divider;
            orderFulfillment
                    .set(enabled, BigDecimal.valueOf(getWeightOf(index)));
            shortTimeFulfillment
                    .set(longtime, shortTimeWeight
                            .divide(BigDecimal.valueOf(reversedDivider), 100, RoundingMode.HALF_EVEN)
                            .multiply(BigDecimal.valueOf(-divider)))
                    .set(constantOne, shortTimeWeight);
            longTimeFulfillment
                    .set(longtime, longTimeWeight
                            .divide(BigDecimal.valueOf(divider), 100, RoundingMode.HALF_EVEN)
                            .multiply(BigDecimal.valueOf(reversedDivider)));
            index++;
        }

        Optimisation.Result result = model.maximise();
        System.out.println(result);
        if (result.getState().isFailure()) {
            throw new IllegalStateException("Optimized data wrong: " + result);
        }
    }

    private void bindLongtimeToEnabled(ExpressionsBasedModel model, int index, Variable enabled, Variable longtime,
            BigDecimal lowerBound) {
        model.addExpression("enabled_longtime_" + index)
                .set(longtime, BigInteger.valueOf(1))
                .set(enabled, BigInteger.valueOf(-1))
                .lower(lowerBound.subtract(BigDecimal.valueOf(1)))
                .upper(BigInteger.valueOf(0));
    }

    private void bindNewAmountToLongtime(ExpressionsBasedModel model, int index, Variable newAmount, Variable longtime,
            int requestedAmount) {
        model.addExpression("newAmount_longtime_" + index)
                .set(newAmount, BigInteger.valueOf(-1))
                .set(longtime, requestedAmount)
                .level(BigInteger.valueOf(0));
    }

    private void bindOldAmountToNewAmount(ExpressionsBasedModel model, int index, Variable newAmount,
            Variable oldAmount, int requestedAmount) {
        model.addExpression("bind_old_" + index)
                .set(newAmount, BigInteger.valueOf(1))
                .set(oldAmount, BigInteger.valueOf(1))
                .level(requestedAmount);
    }

    private void bindOldDeductionToEnabledAndOldValue(ExpressionsBasedModel model, int index, Variable enabled,
            Variable oldAmount, Variable oldDeduction) {
        Variable constantBigInt = model.addVariable("constant_big_int_" + index)
                .integer(true)
                .level(BigInteger.valueOf(BIG_INT));
        model.addExpression("oldDeduction_1_" + index)
                .set(oldDeduction, BigInteger.valueOf(1))
                .lower(BigDecimal.valueOf(0));
        model.addExpression("oldDeduction_2_" + index)
                .set(oldDeduction, BigInteger.valueOf(-1))
                .set(enabled, BigInteger.valueOf(BIG_INT))
                .lower(BigInteger.valueOf(0));
        model.addExpression("oldDeduction_3_" + index)
                .set(oldDeduction, BigInteger.valueOf(-1))
                .set(oldAmount, BigInteger.valueOf(1))
                .lower(BigInteger.valueOf(0));
        model.addExpression("oldDeduction_4_" + index)
                .set(oldDeduction, BigInteger.valueOf(-1))
                .set(oldAmount, BigInteger.valueOf(1))
                .set(constantBigInt, BigInteger.valueOf(-1))
                .set(enabled, BigInteger.valueOf(BIG_INT))
                .upper(BigInteger.valueOf(0));
    }

    private double getWeightOf(int input) {
        if (input > 6) {
            return (1 / Math.pow(2, 6));
        }
        return (1 / Math.pow(2, (input)));
    }

    public static class BlisterData {
        private final int newAmount;
        private final int oldAmount;
        private final Collection<Order> orders;

        public BlisterData(int newAmount, int oldAmount, Collection<Order> orders) {
            this.newAmount = newAmount;
            this.oldAmount = oldAmount;
            this.orders = orders;
        }

        public Collection<Order> getOrders() {
            return orders;
        }

        public int getNewAmount() {
            return newAmount;
        }

        public int getOldAmount() {
            return oldAmount;
        }

        @Override
        public int hashCode() {
            return Objects.hash(newAmount, oldAmount, orders);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof BlisterData)) {
                return false;
            }

            BlisterData other = (BlisterData) obj;

            return Objects.equals(newAmount, other.newAmount) &&
                    Objects.equals(oldAmount, other.oldAmount) &&
                    Objects.equals(orders, other.orders);
        }
    }

    public static class Order {
        private final Long id;
        private final int requestedAmount;
        private final Date orderingDate;
        private final Long longtimeValue;
        private final Long customerAddressId;
        private final Long depotId;
        private final Long shippingParameterId;

        private Variable longtime;
        private Variable enabled;

        public Order(Long id, Double requestedAmount, Date orderingDate, Long longtime, Long customerAddressId,
                Long depotId, Long shippingParameterId) {
            this.id = id;
            this.requestedAmount = requestedAmount != null ? requestedAmount.intValue() : 0;
            this.orderingDate = orderingDate;
            this.longtimeValue = longtime;
            this.customerAddressId = customerAddressId;
            this.depotId = depotId;
            this.shippingParameterId = shippingParameterId;
        }

        public Long getId() {
            return id;
        }

        public int getRequestedAmount() {
            return requestedAmount;
        }

        public Date getOrderingDate() {
            return orderingDate;
        }

        public Long getCustomerAddressId() {
            return customerAddressId;
        }

        public boolean isEnabled() {
            return enabled.getValue().intValue() == 1;
        }

        public int getNewAmount() {
            return (int) Math.round(requestedAmount * longtime.getValue().doubleValue());
        }

        public int getOldAmount() {
            return requestedAmount - getNewAmount();
        }

        public Long getLongtimeValue() {
            return longtimeValue;
        }

        public Long getDepotId() {
            return depotId;
        }

        public Long getShippingParameterId() {
            return shippingParameterId;
        }

        void setLongtime(Variable longtime) {
            this.longtime = longtime;
        }

        void setEnabled(Variable enabled) {
            this.enabled = enabled;
        }

        @Override
        public int hashCode() {
            return Objects.hash(requestedAmount, longtimeValue, orderingDate);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Order)) {
                return false;
            }

            Order other = (Order) obj;

            return Objects.equals(requestedAmount, other.requestedAmount) &&
                    Objects.equals(longtimeValue, other.longtimeValue) &&
                    Objects.equals(orderingDate, other.orderingDate);
        }
    }
}
