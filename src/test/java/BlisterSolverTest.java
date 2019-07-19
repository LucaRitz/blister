import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class BlisterSolverTest {
    private BlisterSolver solver;

    @BeforeEach
    void setUp() {
        solver = new BlisterSolver();
    }

    @ParameterizedTest
    @MethodSource("blisterTest")
    void optimize_getExpected(BlisterTest testData) {
        Map<OrderTest, BlisterSolver.Order> orders = new HashMap<>();
        for (OrderTest order : testData.orders.keySet()) {
            BlisterSolver.Order orderModel = new BlisterSolver.Order(order.expectedAmount, 0L, order.longtime);
            orders.put(order, orderModel);
        }

        BlisterSolver.BlisterData data = new BlisterSolver.BlisterData(testData.newAmount, testData.oldAmount,
                new ArrayList<>(orders.values()));

        // Act
        solver.optimize(Collections.singleton(data));

        // Assert
        int allNewAmount = 0;
        int allOldAmount = 0;
        for (Map.Entry<OrderTest, ExpectedResult> entry : testData.orders.entrySet()) {
            BlisterSolver.Order order = orders.get(entry.getKey());
            ExpectedResult expected = entry.getValue();
            int newAmount = order.newAmount.getValue().intValue();
            int oldAmount = order.oldAmount.getValue().intValue();
            allNewAmount += newAmount;
            allOldAmount += oldAmount;

            assertEquals(expected.newAmount, newAmount);
            assertEquals(expected.oldAmount, oldAmount);
            assertEquals(entry.getKey().expectedAmount, newAmount + oldAmount);
        }
        assertTrue(allNewAmount <= testData.newAmount);
        assertTrue(allOldAmount <= testData.oldAmount);
    }

    private static Stream<Arguments> blisterTest() {
        return Stream.of(
                Arguments.of(optimize_noNeedForNewBlisterAndEnoughOldAvailable_getAllOld()),
                Arguments.of(optimize_noNeedForNewBlisterAndEnoughOldAvailableAndNoNewAvailable_getAllOld()),
                Arguments.of(optimize_justNewBlisterRequestedAndHasEnough_getAllNew()),
                Arguments.of(optimize_justNewBlisterRequestedAndHasEnoughAndNoOldAvailable_getAllNew()),
                Arguments.of(optimize_hasEnoughNewAndOldBlister_getExpectedNewAndOld()),
                Arguments.of(optimize_hasEnoughNewBlisterAndNoOld_getAllNew())
        );
    }

    private static BlisterTest optimize_noNeedForNewBlisterAndEnoughOldAvailable_getAllOld() {
        OrderTest orderA = new OrderTest(0, 10);
        OrderTest orderB = new OrderTest(0, 15);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(0, 10));
        orders.put(orderB, new ExpectedResult(0, 15));

        return new BlisterTest(100, 25, orders);
    }

    private static BlisterTest optimize_noNeedForNewBlisterAndEnoughOldAvailableAndNoNewAvailable_getAllOld() {
        OrderTest orderA = new OrderTest(0, 10);
        OrderTest orderB = new OrderTest(0, 15);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(0, 10));
        orders.put(orderB, new ExpectedResult(0, 15));

        return new BlisterTest(0, 25, orders);
    }

    private static BlisterTest optimize_justNewBlisterRequestedAndHasEnough_getAllNew() {
        OrderTest orderA = new OrderTest(100, 10);
        OrderTest orderB = new OrderTest(100, 15);
        OrderTest orderC = new OrderTest(100, 20);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(10, 0));
        orders.put(orderB, new ExpectedResult(15, 0));
        orders.put(orderC, new ExpectedResult(20, 0));

        return new BlisterTest(45, 100, orders);
    }

    private static BlisterTest optimize_justNewBlisterRequestedAndHasEnoughAndNoOldAvailable_getAllNew() {
        OrderTest orderA = new OrderTest(100, 10);
        OrderTest orderB = new OrderTest(100, 15);
        OrderTest orderC = new OrderTest(100, 20);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(10, 0));
        orders.put(orderB, new ExpectedResult(15, 0));
        orders.put(orderC, new ExpectedResult(20, 0));

        return new BlisterTest(45, 0, orders);
    }

    private static BlisterTest optimize_hasEnoughNewAndOldBlister_getExpectedNewAndOld() {
        OrderTest orderA = new OrderTest(80, 10);
        OrderTest orderB = new OrderTest(60, 15);
        OrderTest orderC = new OrderTest(0, 20);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(8, 2));
        orders.put(orderB, new ExpectedResult(9, 6));
        orders.put(orderC, new ExpectedResult(0, 20));

        return new BlisterTest(17, 100, orders);
    }

    private static BlisterTest optimize_hasEnoughNewBlisterAndNoOld_getAllNew() {
        OrderTest orderA = new OrderTest(80, 10);
        OrderTest orderB = new OrderTest(60, 15);
        OrderTest orderC = new OrderTest(0, 20);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(10, 0));
        orders.put(orderB, new ExpectedResult(15, 0));
        orders.put(orderC, new ExpectedResult(20, 0));

        return new BlisterTest(45,0, orders);
    }

    private static class BlisterTest {
        private final int newAmount;
        private final int oldAmount;
        private Map<OrderTest, ExpectedResult> orders;

        BlisterTest(int newAmount, int oldAmount, Map<OrderTest, ExpectedResult> orders) {
            this.newAmount = newAmount;
            this.oldAmount = oldAmount;
            this.orders = orders;
        }
    }

    private static class OrderTest {
        private final long longtime;
        private final int expectedAmount;

        OrderTest(long longtime, int expectedAmount) {
            this.longtime = longtime;
            this.expectedAmount = expectedAmount;
        }
    }

    private static class ExpectedResult {
        private final int newAmount;
        private final int oldAmount;

        ExpectedResult(int newAmount, int oldAmount) {
            this.newAmount = newAmount;
            this.oldAmount = oldAmount;
        }
    }
}
