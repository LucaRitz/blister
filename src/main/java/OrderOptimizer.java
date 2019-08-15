import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class OrderOptimizer {

    private static final BigDecimal BIG_INT = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100.0d);
    private static final BigDecimal FULFILLMENT_WEIGHT = BigDecimal.valueOf(10_000);
    private static final BigDecimal SHORTTIME_WEIGHT = BigDecimal.ONE;
    private static final BigDecimal LONGTIME_WEIGHT = BigDecimal.ONE;
    private static final int DIVISION_SCALE = 10_000;
    private static final int SCALE = DIVISION_SCALE;

    public void optimize(Collection<BlisterData> data) {
        data.forEach(this::optimize);
    }

    private void optimize(BlisterData blisterData) {
        Optimisation.Options options = new Optimisation.Options();
        options.mip_gap = 0;
        final ExpressionsBasedModel model = new ExpressionsBasedModel(options);

        // Object function
        Expression orderFulfillment = model.addExpression("object_enabled")
                .weight(FULFILLMENT_WEIGHT);
        Expression shortTimeFulfillment = model.addExpression("object_shortTime")
                .weight(SHORTTIME_WEIGHT);
        Expression longTimeFulfillment = model.addExpression("object_longTime")
                .weight(LONGTIME_WEIGHT);

        Variable constantBigInt = Variable.make("constant_big_int").integer(true).level(BIG_INT);
        model.addVariable(constantBigInt);
        /*Variable constantBigInt = model.addVariable("constant_big_int")
                .integer(true)
                .level(BIG_INT);*/

        // n_1 + ,..., + n_n <= N
        Expression allNewBlisterAreLowerOrEqualsMaximum = model.addExpression("newBlistersMaximum")
                .upper(BigDecimal.valueOf(blisterData.newAmount));
        // a_1 + ,..., + a_n <= A
        Expression allOldBlisterAreLowerOrEqualsMaximum = model.addExpression("oldBlistersMaximum")
                .upper(BigDecimal.valueOf(blisterData.oldAmount));
        int index = 0;

        List<Order> sortedByDate = new ArrayList<>(blisterData.orders);
        sortedByDate.sort(Comparator.comparing(Order::getOrderingDate));

        for (Order order : sortedByDate) {
            /*Variable enabled = model.addVariable("enabled_" + index)
                    .binary();*/
            Variable enabled = Variable.makeBinary("enabled_" + index);
            model.addVariable(enabled);

            /*Variable longtime = model.addVariable("longtime_" + index)
                    .lower(0);*/
            Variable longtime = Variable.make("longtime_" + index)
                    .lower(0);
            model.addVariable(longtime);

            BigDecimal longTimeWeight;
            BigDecimal shortTimeWeight;
            BigDecimal lowerBound;
            if (order.getLongtimeValue() != null) {
                lowerBound = BigDecimal.valueOf(order.getLongtimeValue())
                        .divide(HUNDRED, DIVISION_SCALE, RoundingMode.HALF_UP)
                        .setScale(SCALE, RoundingMode.UNNECESSARY);
                longTimeWeight = BigDecimal.ZERO
                        .setScale(SCALE, RoundingMode.UNNECESSARY);
                shortTimeWeight = BigDecimal.ONE
                        .setScale(SCALE, RoundingMode.UNNECESSARY);
            } else {
                lowerBound = BigDecimal.ZERO
                        .setScale(SCALE, RoundingMode.UNNECESSARY);
                longTimeWeight = BigDecimal.ONE
                        .setScale(SCALE, RoundingMode.UNNECESSARY);
                shortTimeWeight = BigDecimal.ZERO
                        .setScale(SCALE, RoundingMode.UNNECESSARY);
            }

            order.setLongtime(longtime);
            order.setEnabled(enabled);

            // enabled = 1 -> minimum <= longtime <= 1
            bindLongtimeToEnabled(model, index, enabled, longtime, lowerBound);

            /*Variable oldDeduction = model.addVariable("oldDeduction_" + index)
                    .integer(true)
                    .lower(BigDecimal.ZERO);*/

            Variable oldDeduction = Variable.make("oldDeduction_" + index)
                    .integer(true)
                    .lower(BigDecimal.ZERO);
            model.addVariable(oldDeduction);
            // oldDeduction = enabled * oldValue
            bindOldDeductionToEnabledAndOldValue(model, index, enabled, oldDeduction, longtime, constantBigInt,
                    order.requestedAmount);

            allNewBlisterAreLowerOrEqualsMaximum
                    .set(longtime, BigDecimal.valueOf(order.requestedAmount));
            allOldBlisterAreLowerOrEqualsMaximum
                    .set(oldDeduction, BigDecimal.ONE);

            // Update Object-function
            int divider = (index + 1);
            int reversedDivider = sortedByDate.size() + 1 - divider;
            orderFulfillment
                    .set(enabled, getWeightOf(index));
            shortTimeFulfillment
                    .set(longtime, shortTimeWeight
                            .divide(BigDecimal.valueOf(reversedDivider), DIVISION_SCALE, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(-divider)));
            longTimeFulfillment
                    .set(longtime, longTimeWeight
                            .divide(BigDecimal.valueOf(divider), DIVISION_SCALE, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(reversedDivider)));
            index++;
        }

        Optimisation.Result result = model.maximise();
        System.out.println(model.validate(result));
        System.out.println(result);
        if (result.getState().isFailure()) {
            throw new IllegalStateException("Optimized data wrong: " + result);
        }
    }

    private void bindLongtimeToEnabled(ExpressionsBasedModel model, int index, Variable enabled, Variable longtime,
            BigDecimal lowerBound) {
        model.addExpression("enabled_longtime_" + index)
                .set(longtime, BigDecimal.ONE)
                .set(enabled, BigDecimal.valueOf(-1))
                .lower(lowerBound.subtract(BigDecimal.ONE))
                .upper(BigDecimal.ZERO);
    }

    private void bindOldDeductionToEnabledAndOldValue(ExpressionsBasedModel model, int index, Variable enabled,
            Variable oldDeduction, Variable longtime, Variable constantBigInt, int requestedAmount) {
       /* Variable constantReqAmount = model.addVariable("constant_req_int_" + index)
                .integer(true)
                .level(BigDecimal.valueOf(requestedAmount));*/
        Variable constantReqAmount = Variable.make("constant_req_int_" + index)
                .integer(true)
                .level(BigDecimal.valueOf(requestedAmount));
        model.addVariable(constantReqAmount);

        model.addExpression("oldDeduction_2_" + index)
                .set(oldDeduction, BigDecimal.valueOf(-1))
                .set(enabled, BIG_INT)
                .lower(BigDecimal.ZERO);
        model.addExpression("oldDeduction_3_" + index)
                .set(oldDeduction, BigDecimal.valueOf(-1))
                .set(constantReqAmount, BigDecimal.ONE)
                .set(longtime, BigDecimal.valueOf(-requestedAmount))
                .lower(BigDecimal.ZERO);
        model.addExpression("oldDeduction_4_" + index)
                .set(oldDeduction, BigDecimal.valueOf(-1))
                .set(constantReqAmount, BigDecimal.ONE)
                .set(longtime, BigDecimal.valueOf(-requestedAmount))
                .set(constantBigInt, BigDecimal.valueOf(-1))
                .set(enabled, BIG_INT)
                .upper(BigDecimal.ZERO);
    }

    private BigDecimal getWeightOf(int input) {
        int x = input > 6 ? 6 : input;
        BigDecimal pow = BigDecimal.valueOf(2)
                .setScale(SCALE, RoundingMode.UNNECESSARY)
                .pow(x);
        BigDecimal one = BigDecimal.ONE
                .setScale(SCALE, RoundingMode.UNNECESSARY);
        return one.divide(pow, DIVISION_SCALE, RoundingMode.HALF_UP);
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
