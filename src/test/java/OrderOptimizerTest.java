import com.google.common.collect.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class OrderOptimizerTest {
    private OrderOptimizer optimizer;

    @BeforeEach
    void setUp() {
        optimizer = new OrderOptimizer();
    }

    @ParameterizedTest
    @MethodSource("blisterTest")
    void optimize_getExpected(BlisterTest testData) {
        Map<OrderTest, OrderOptimizer.Order> orders = new HashMap<>();
        for (OrderTest order : testData.orders.keySet()) {
            OrderOptimizer.Order orderModel = new OrderOptimizer.Order(null, (double) order.expectedAmount,
                    order.orderingDate, order.longtime, null, null, null);
            orders.put(order, orderModel);
        }

        OrderOptimizer.BlisterData data = new OrderOptimizer.BlisterData(testData.newAmount, testData.oldAmount,
                new HashSet<>(orders.values()));

        // Act
        optimizer.optimize(Collections.singleton(data));

        // Assert
        int allNewAmount = 0;
        int allOldAmount = 0;
        for (Map.Entry<OrderTest, ExpectedResult> entry : testData.orders.entrySet()) {
            OrderOptimizer.Order order = orders.get(entry.getKey());
            if (!order.isEnabled()) {
                assertTrue(testData.incomplete.contains(entry.getKey()));
                continue;
            }
            ExpectedResult expected = entry.getValue();
            int newAmount = order.getNewAmount();
            int oldAmount = order.getOldAmount();
            allNewAmount += newAmount;
            allOldAmount += oldAmount;

            assertEquals(expected.newAmount, newAmount);
            assertEquals(expected.oldAmount, oldAmount);
            System.out.println("---Order---");
            System.out.println("Expected new: " + expected.newAmount);
            System.out.println("New: " + newAmount);
            System.out.println("Expected old: " + expected.oldAmount);
            System.out.println("Old: " + oldAmount);
            System.out.println();

            assertEquals(entry.getKey().expectedAmount, newAmount + oldAmount);
        }

        assertTrue(allNewAmount <= testData.newAmount);
        assertTrue(allOldAmount <= testData.oldAmount);
    }

    private static Stream<Arguments> blisterTest() {
        Stream<Arguments> arguments = optimize_noOldGivenAndRequireAllNewWithAllPossiblePercentages_getAllNew();
        Stream<Arguments> arguments2 = Stream.of(
                Arguments.of(optimize_noNeedForNewBlisterAndEnoughOldAvailable_getAllOld()),
                Arguments.of(optimize_noNeedForNewBlisterAndEnoughOldAvailableAndNoNewAvailable_getAllOld()),
                Arguments.of(optimize_justNewBlisterRequestedAndHasEnough_getAllNew()),
                Arguments.of(optimize_justNewBlisterRequestedAndHasEnoughAndNoOldAvailable_getAllNew()),
                Arguments.of(optimize_hasEnoughNewAndOldBlister_getExpectedNewAndOld()),
                Arguments.of(optimize_hasEnoughNewAndNoOldBlister_getAllNew()),
                Arguments.of(optimize_hasEnoughNewAndAFewOldBlisters_oldestOrdersGetNewBlisters()),
                Arguments.of(optimize_hasEnoughNewAndAFewOldBlisters_allOldBlistersAreUsed()),
                Arguments
                        .of(optimize_noLongtimeValueIsSetAndEnoughNewAndEnoughOldBlistersAreAvailable_getAllNewBlisters()),
                Arguments.of(optimize_noLongtimeValueIsSetAndEnoughNewAndNoOldBlistersAreAvailable_getAllNewBlisters()),
                Arguments.of(optimize_noLongtimeValueIsSetAndEnoughOldAndNoNewBlistersAreAvailable_getAllOldBlisters()),
                Arguments.of(optimize_noLongtimeValueIsSetAndNotEnoughNewBlistersAreAvailable_getNewAndOldBlisters()),
                Arguments.of(optimize_mixedLongtimeValuesGiven_getNewAndOldBlisters()),
                Arguments
                        .of(optimize_noLongtimeValueIsSetAndEnoughNewBlistersAvailableAndMuchBlistersExpected_getNewBlisters()),
                Arguments
                        .of(optimize_noLongtimeValueIsSetAndEnoughNewBlistersAvailableAndThreeOrdersWithMuchBlistersExpected_getNewBlisters()),
                Arguments
                        .of(optimize_mixedLongtimeValuesGivenAndEnoughNewBlistersAvailableAndThreeOrdersWithMuchBlistersExpected_getNewAndOldBlisters()),
                Arguments.of(optimize_justEnoughForFirstOrder_firstIsComplete()),
                Arguments.of(optimize_justEnoughForFirstAndThirdOrder_firstAndThirdAreComplete()),
                Arguments.of(optimize_justEnoughForFirstOrderAndNoLongtimeSet_firstIsComplete()),
                Arguments.of(optimize_justEnoughForFirstAndThirdOrderAndNoLongtimeSet_firstAndThirdAreComplete()),
                Arguments.of(optimize_justEnoughForFirstOrderMixedOldAndNew_firstIsComplete()),
                Arguments.of(optimize_justEnoughForFirstOrderMixedOldAndNewAndNoLongtimeSet_firstIsComplete()),
                Arguments.of(optimize_veryLargeOrder_getExpected()),
                Arguments.of(optimize_veryLargeOrder2_getExpected()),
                Arguments.of(optimize_noBlisterAvailable_allDisabled()),
                Arguments.of(optimize_noOrderIsFulfillable_allDisabled()),
                Arguments.of(optimize_this_test_is_wrong_since_45_0_0())
        );
        arguments = Stream.concat(arguments, arguments2);

        Stream<Arguments> argumentsFailsIn4400 = optimize_this_test_fails_sometimes_in_44_0_0();
        arguments = Stream.concat(arguments, argumentsFailsIn4400);

        return arguments;
    }

    private static BlisterTest optimize_this_test_is_wrong_since_45_0_0() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(60L, toDate(currentDate), 20);
        OrderTest orderB = new OrderTest(10L, toDate(currentDate.plusDays(1)), 10);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(19, 1));
        orders.put(orderB, new ExpectedResult(1, 9));

        return new BlisterTest(30, 10, orders);
    }

    private static Stream<Arguments> optimize_this_test_fails_sometimes_in_44_0_0() {
        LocalDate currentDate = LocalDate.now();
        Arguments[] args = new Arguments[10000];
        for (int i = 0; i < args.length; i++) {
            OrderTest orderA = new OrderTest(69L, toDate(currentDate), 20);
            OrderTest orderB = new OrderTest(10L, toDate(currentDate.plusDays(1)), 10);

            Map<OrderTest, ExpectedResult> orders = new HashMap<>();
            orders.put(orderA, new ExpectedResult(20, 0));
            orders.put(orderB, new ExpectedResult(10, 0));
            args[i] = Arguments.of(new BlisterTest(30, 0, orders));
        }

        return Stream.of(args);
    }

    private static Stream<Arguments> optimize_noOldGivenAndRequireAllNewWithAllPossiblePercentages_getAllNew() {
        int maxPercentage = 100;
        Arguments[] arguments = new Arguments[maxPercentage+1];

        for(int i = 0; i <= maxPercentage; i++) {
            arguments[i] = Arguments.of(createWithPercentage(i));
        }
        return Stream.of(arguments);
    }

    private static BlisterTest createWithPercentage(long percentage) {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(percentage, toDate(currentDate), 20);
        OrderTest orderB = new OrderTest(10L, toDate(currentDate.plusDays(1)), 10);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(20, 0));
        orders.put(orderB, new ExpectedResult(10, 0));

        return new BlisterTest(30, 0, orders);
    }

    private static BlisterTest optimize_noNeedForNewBlisterAndEnoughOldAvailable_getAllOld() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(0L, toDate(currentDate), 10);
        OrderTest orderB = new OrderTest(0L, toDate(currentDate.plusDays(1)), 15);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(0, 10));
        orders.put(orderB, new ExpectedResult(0, 15));

        return new BlisterTest(100, 25, orders);
    }

    private static BlisterTest optimize_noNeedForNewBlisterAndEnoughOldAvailableAndNoNewAvailable_getAllOld() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(0L, toDate(currentDate), 10);
        OrderTest orderB = new OrderTest(0L, toDate(currentDate.plusDays(1)), 15);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(0, 10));
        orders.put(orderB, new ExpectedResult(0, 15));

        return new BlisterTest(0, 25, orders);
    }

    private static BlisterTest optimize_justNewBlisterRequestedAndHasEnough_getAllNew() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(100L, toDate(currentDate), 10);
        OrderTest orderB = new OrderTest(100L, toDate(currentDate.plusDays(1)), 15);
        OrderTest orderC = new OrderTest(100L, toDate(currentDate.plusDays(2)), 20);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(10, 0));
        orders.put(orderB, new ExpectedResult(15, 0));
        orders.put(orderC, new ExpectedResult(20, 0));

        return new BlisterTest(45, 100, orders);
    }

    private static BlisterTest optimize_justNewBlisterRequestedAndHasEnoughAndNoOldAvailable_getAllNew() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(100L, toDate(currentDate), 10);
        OrderTest orderB = new OrderTest(100L, toDate(currentDate.plusDays(1)), 15);
        OrderTest orderC = new OrderTest(100L, toDate(currentDate.plusDays(2)), 20);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(10, 0));
        orders.put(orderB, new ExpectedResult(15, 0));
        orders.put(orderC, new ExpectedResult(20, 0));

        return new BlisterTest(45, 0, orders);
    }

    private static BlisterTest optimize_hasEnoughNewAndOldBlister_getExpectedNewAndOld() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(80L, toDate(currentDate), 10);
        OrderTest orderB = new OrderTest(60L, toDate(currentDate.plusDays(1)), 15);
        OrderTest orderC = new OrderTest(0L, toDate(currentDate.plusDays(2)), 20);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(8, 2));
        orders.put(orderB, new ExpectedResult(9, 6));
        orders.put(orderC, new ExpectedResult(0, 20));

        return new BlisterTest(17, 100, orders);
    }

    private static BlisterTest optimize_hasEnoughNewAndNoOldBlister_getAllNew() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(80L, toDate(currentDate), 10);
        OrderTest orderB = new OrderTest(60L, toDate(currentDate.plusDays(1)), 15);
        OrderTest orderC = new OrderTest(0L, toDate(currentDate.plusDays(2)), 20);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(10, 0));
        orders.put(orderB, new ExpectedResult(15, 0));
        orders.put(orderC, new ExpectedResult(20, 0));

        return new BlisterTest(45, 0, orders);
    }

    private static BlisterTest optimize_hasEnoughNewAndAFewOldBlisters_oldestOrdersGetNewBlisters() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(80L, toDate(currentDate), 10);
        OrderTest orderB = new OrderTest(60L, toDate(currentDate.plusDays(1)), 15);
        OrderTest orderC = new OrderTest(0L, toDate(currentDate.plusDays(2)), 20);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(10, 0));
        orders.put(orderB, new ExpectedResult(15, 0));
        orders.put(orderC, new ExpectedResult(10, 10));

        return new BlisterTest(35, 10, orders);
    }

    private static BlisterTest optimize_hasEnoughNewAndAFewOldBlisters_allOldBlistersAreUsed() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(30L, toDate(currentDate), 10);
        OrderTest orderB = new OrderTest(40L, toDate(currentDate.plusDays(1)), 15);
        OrderTest orderC = new OrderTest(20L, toDate(currentDate.plusDays(2)), 20);
        OrderTest orderD = new OrderTest(80L, toDate(currentDate.plusDays(3)), 9);
        OrderTest orderE = new OrderTest(90L, toDate(currentDate.plusDays(4)), 33);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(10, 0));
        orders.put(orderB, new ExpectedResult(15, 0));
        orders.put(orderC, new ExpectedResult(13, 7));
        orders.put(orderD, new ExpectedResult(8, 1));
        orders.put(orderE, new ExpectedResult(30, 3));

        return new BlisterTest(100, 11, orders);
    }

    private static BlisterTest optimize_noLongtimeValueIsSetAndEnoughNewAndEnoughOldBlistersAreAvailable_getAllNewBlisters() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(null, toDate(currentDate), 15);
        OrderTest orderB = new OrderTest(null, toDate(currentDate.plusDays(1)), 10);
        OrderTest orderC = new OrderTest(null, toDate(currentDate.plusDays(2)), 30);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(15, 0));
        orders.put(orderB, new ExpectedResult(10, 0));
        orders.put(orderC, new ExpectedResult(30, 0));

        return new BlisterTest(56, 100, orders);
    }

    private static BlisterTest optimize_noLongtimeValueIsSetAndEnoughNewAndNoOldBlistersAreAvailable_getAllNewBlisters() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(null, toDate(currentDate), 15);
        OrderTest orderB = new OrderTest(null, toDate(currentDate.plusDays(1)), 10);
        OrderTest orderC = new OrderTest(null, toDate(currentDate.plusDays(2)), 30);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(15, 0));
        orders.put(orderB, new ExpectedResult(10, 0));
        orders.put(orderC, new ExpectedResult(30, 0));

        return new BlisterTest(56, 0, orders);
    }

    private static BlisterTest optimize_noLongtimeValueIsSetAndEnoughOldAndNoNewBlistersAreAvailable_getAllOldBlisters() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(null, toDate(currentDate), 15);
        OrderTest orderB = new OrderTest(null, toDate(currentDate.plusDays(1)), 10);
        OrderTest orderC = new OrderTest(null, toDate(currentDate.plusDays(2)), 30);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(0, 15));
        orders.put(orderB, new ExpectedResult(0, 10));
        orders.put(orderC, new ExpectedResult(0, 30));

        return new BlisterTest(0, 55, orders);
    }

    private static BlisterTest optimize_noLongtimeValueIsSetAndNotEnoughNewBlistersAreAvailable_getNewAndOldBlisters() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(null, toDate(currentDate), 15);
        OrderTest orderB = new OrderTest(null, toDate(currentDate.plusDays(1)), 10);
        OrderTest orderC = new OrderTest(null, toDate(currentDate.plusDays(2)), 30);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(15, 0));
        orders.put(orderB, new ExpectedResult(5, 5));
        orders.put(orderC, new ExpectedResult(0, 30));

        return new BlisterTest(20, 35, orders);
    }

    private static BlisterTest optimize_mixedLongtimeValuesGiven_getNewAndOldBlisters() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(60L, toDate(currentDate), 15);
        OrderTest orderB = new OrderTest(null, toDate(currentDate.plusDays(1)), 10);
        OrderTest orderC = new OrderTest(50L, toDate(currentDate.plusDays(2)), 30);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(9, 6));
        orders.put(orderB, new ExpectedResult(1, 9));
        orders.put(orderC, new ExpectedResult(15, 15));

        return new BlisterTest(25, 100, orders);
    }

    private static BlisterTest optimize_noLongtimeValueIsSetAndEnoughNewBlistersAvailableAndMuchBlistersExpected_getNewBlisters() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(null, toDate(currentDate), 100000);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(100000, 0));

        return new BlisterTest(Integer.MAX_VALUE, Integer.MAX_VALUE, orders);
    }

    private static BlisterTest optimize_noLongtimeValueIsSetAndEnoughNewBlistersAvailableAndThreeOrdersWithMuchBlistersExpected_getNewBlisters() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(null, toDate(currentDate), 100000);
        OrderTest orderB = new OrderTest(null, toDate(currentDate.plusDays(1)), 100000);
        OrderTest orderC = new OrderTest(null, toDate(currentDate.plusDays(2)), 100000);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(100000, 0));
        orders.put(orderB, new ExpectedResult(100000, 0));
        orders.put(orderC, new ExpectedResult(100000, 0));

        return new BlisterTest(Integer.MAX_VALUE, Integer.MAX_VALUE, orders);
    }

    private static BlisterTest optimize_mixedLongtimeValuesGivenAndEnoughNewBlistersAvailableAndThreeOrdersWithMuchBlistersExpected_getNewAndOldBlisters() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(null, toDate(currentDate), 100000);
        OrderTest orderB = new OrderTest(80L, toDate(currentDate.plusDays(1)), 100000);
        OrderTest orderC = new OrderTest(60L, toDate(currentDate.plusDays(2)), 100000);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(100000, 0));
        orders.put(orderB, new ExpectedResult(80000, 20000));
        orders.put(orderC, new ExpectedResult(60000, 40000));

        return new BlisterTest(Integer.MAX_VALUE, Integer.MAX_VALUE, orders);
    }

    private static BlisterTest optimize_justEnoughForFirstOrder_firstIsComplete() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(10L, toDate(currentDate), 10);
        OrderTest orderB = new OrderTest(20L, toDate(currentDate.plusDays(1)), 15);
        OrderTest orderC = new OrderTest(60L, toDate(currentDate.plusDays(2)), 10);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(10, 0));
        orders.put(orderB, new ExpectedResult(0, 0));
        orders.put(orderC, new ExpectedResult(0, 0));

        return new BlisterTest(10, 0, orders, Sets.newHashSet(orderB, orderC));
    }

    private static BlisterTest optimize_justEnoughForFirstAndThirdOrder_firstAndThirdAreComplete() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(10L, toDate(currentDate), 10);
        OrderTest orderB = new OrderTest(20L, toDate(currentDate.plusDays(1)), 15);
        OrderTest orderC = new OrderTest(60L, toDate(currentDate.plusDays(2)), 10);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(10, 0));
        orders.put(orderB, new ExpectedResult(0, 0));
        orders.put(orderC, new ExpectedResult(10, 0));

        return new BlisterTest(20, 0, orders, Sets.newHashSet(orderB));
    }

    private static BlisterTest optimize_justEnoughForFirstOrderAndNoLongtimeSet_firstIsComplete() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(null, toDate(currentDate), 10);
        OrderTest orderB = new OrderTest(null, toDate(currentDate.plusDays(1)), 15);
        OrderTest orderC = new OrderTest(null, toDate(currentDate.plusDays(2)), 10);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(10, 0));
        orders.put(orderB, new ExpectedResult(0, 0));
        orders.put(orderC, new ExpectedResult(0, 0));

        return new BlisterTest(10, 0, orders, Sets.newHashSet(orderB, orderC));
    }

    private static BlisterTest optimize_justEnoughForFirstAndThirdOrderAndNoLongtimeSet_firstAndThirdAreComplete() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(null, toDate(currentDate), 10);
        OrderTest orderB = new OrderTest(null, toDate(currentDate.plusDays(1)), 15);
        OrderTest orderC = new OrderTest(null, toDate(currentDate.plusDays(2)), 10);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(10, 0));
        orders.put(orderB, new ExpectedResult(0, 0));
        orders.put(orderC, new ExpectedResult(10, 0));

        return new BlisterTest(20, 0, orders, Sets.newHashSet(orderB));
    }

    private static BlisterTest optimize_justEnoughForFirstOrderMixedOldAndNew_firstIsComplete() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(60L, toDate(currentDate), 10);
        OrderTest orderB = new OrderTest(0L, toDate(currentDate.plusDays(1)), 5);
        OrderTest orderC = new OrderTest(0L, toDate(currentDate.plusDays(2)), 5);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(7, 3));
        orders.put(orderB, new ExpectedResult(0, 0));
        orders.put(orderC, new ExpectedResult(0, 0));

        return new BlisterTest(11, 3, orders, Sets.newHashSet(orderB, orderC));
    }

    private static BlisterTest optimize_justEnoughForFirstOrderMixedOldAndNewAndNoLongtimeSet_firstIsComplete() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(null, toDate(currentDate), 10);
        OrderTest orderB = new OrderTest(null, toDate(currentDate.plusDays(1)), 5);
        OrderTest orderC = new OrderTest(null, toDate(currentDate.plusDays(2)), 5);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(10, 0));
        orders.put(orderB, new ExpectedResult(0, 0));
        orders.put(orderC, new ExpectedResult(0, 0));

        return new BlisterTest(11, 3, orders, Sets.newHashSet(orderB, orderC));
    }

    private static BlisterTest optimize_veryLargeOrder_getExpected() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(null, toDate(currentDate), 10);
        OrderTest orderB = new OrderTest(null, toDate(currentDate.plusDays(1)), 10);
        OrderTest orderC = new OrderTest(null, toDate(currentDate.plusDays(2)), 20);
        OrderTest orderD = new OrderTest(null, toDate(currentDate.plusDays(3)), 30);
        OrderTest orderE = new OrderTest(null, toDate(currentDate.plusDays(4)), 5);
        OrderTest orderF = new OrderTest(null, toDate(currentDate.plusDays(5)), 5);
        OrderTest orderG = new OrderTest(null, toDate(currentDate.plusDays(6)), 5);
        OrderTest orderH = new OrderTest(null, toDate(currentDate.plusDays(7)), 5);
        OrderTest orderI = new OrderTest(null, toDate(currentDate.plusDays(8)), 5);
        OrderTest orderJ = new OrderTest(null, toDate(currentDate.plusDays(9)), 5);
        OrderTest orderK = new OrderTest(null, toDate(currentDate.plusDays(10)), 5);
        OrderTest orderL = new OrderTest(null, toDate(currentDate.plusDays(11)), 5);
        OrderTest orderM = new OrderTest(null, toDate(currentDate.plusDays(12)), 5);
        OrderTest orderN = new OrderTest(null, toDate(currentDate.plusDays(13)), 5);
        OrderTest orderO = new OrderTest(null, toDate(currentDate.plusDays(14)), 5);
        OrderTest orderP = new OrderTest(null, toDate(currentDate.plusDays(14)), 5);
        OrderTest orderQ = new OrderTest(null, toDate(currentDate.plusDays(14)), 5);
        OrderTest orderR = new OrderTest(null, toDate(currentDate.plusDays(14)), 5);
        OrderTest orderS = new OrderTest(null, toDate(currentDate.plusDays(15)), 5);
        OrderTest orderT = new OrderTest(null, toDate(currentDate.plusDays(15)), 5);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(10, 0));
        orders.put(orderB, new ExpectedResult(0, 10));
        orders.put(orderC, new ExpectedResult(0, 20));
        orders.put(orderD, new ExpectedResult(0, 30));
        orders.put(orderE, new ExpectedResult(0, 5));
        orders.put(orderF, new ExpectedResult(0, 5));
        orders.put(orderG, new ExpectedResult(0, 5));
        orders.put(orderH, new ExpectedResult(0, 5));
        orders.put(orderI, new ExpectedResult(0, 5));
        orders.put(orderJ, new ExpectedResult(0, 5));
        orders.put(orderK, new ExpectedResult(0, 5));
        orders.put(orderL, new ExpectedResult(0, 5));
        orders.put(orderM, new ExpectedResult(0, 5));
        orders.put(orderN, new ExpectedResult(0, 5));
        orders.put(orderO, new ExpectedResult(0, 5));
        orders.put(orderP, new ExpectedResult(0, 5));
        orders.put(orderQ, new ExpectedResult(0, 5));
        orders.put(orderR, new ExpectedResult(0, 5));
        orders.put(orderS, new ExpectedResult(0, 5));
        orders.put(orderT, new ExpectedResult(0, 5));

        return new BlisterTest(10, 200, orders);
    }

    private static BlisterTest optimize_veryLargeOrder2_getExpected() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(null, toDate(currentDate), 10);
        OrderTest orderB = new OrderTest(30L, toDate(currentDate.plusDays(1)), 10);
        OrderTest orderC = new OrderTest(60L, toDate(currentDate.plusDays(2)), 20);
        OrderTest orderD = new OrderTest(10L, toDate(currentDate.plusDays(3)), 10);
        OrderTest orderE = new OrderTest(30L, toDate(currentDate.plusDays(4)), 5);
        OrderTest orderF = new OrderTest(null, toDate(currentDate.plusDays(5)), 5);
        OrderTest orderG = new OrderTest(null, toDate(currentDate.plusDays(6)), 5);
        OrderTest orderH = new OrderTest(null, toDate(currentDate.plusDays(7)), 5);
        OrderTest orderI = new OrderTest(null, toDate(currentDate.plusDays(8)), 5);
        OrderTest orderJ = new OrderTest(null, toDate(currentDate.plusDays(9)), 5);
        OrderTest orderK = new OrderTest(40L, toDate(currentDate.plusDays(10)), 5);
        OrderTest orderL = new OrderTest(null, toDate(currentDate.plusDays(11)), 5);
        OrderTest orderM = new OrderTest(null, toDate(currentDate.plusDays(12)), 5);
        OrderTest orderN = new OrderTest(null, toDate(currentDate.plusDays(13)), 5);
        OrderTest orderO = new OrderTest(null, toDate(currentDate.plusDays(14)), 5);
        OrderTest orderP = new OrderTest(null, toDate(currentDate.plusDays(14)), 5);
        OrderTest orderQ = new OrderTest(null, toDate(currentDate.plusDays(14)), 5);
        OrderTest orderR = new OrderTest(null, toDate(currentDate.plusDays(14)), 5);
        OrderTest orderS = new OrderTest(null, toDate(currentDate.plusDays(15)), 5);
        OrderTest orderT = new OrderTest(null, toDate(currentDate.plusDays(15)), 5);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(10, 0));
        orders.put(orderB, new ExpectedResult(3, 7));
        orders.put(orderC, new ExpectedResult(12, 8));
        orders.put(orderD, new ExpectedResult(1, 9));
        orders.put(orderE, new ExpectedResult(2, 3));
        orders.put(orderF, new ExpectedResult(0, 5));
        orders.put(orderG, new ExpectedResult(0, 5));
        orders.put(orderH, new ExpectedResult(0, 5));
        orders.put(orderI, new ExpectedResult(0, 5));
        orders.put(orderJ, new ExpectedResult(0, 5));
        orders.put(orderK, new ExpectedResult(2, 3));
        orders.put(orderL, new ExpectedResult(0, 5));
        orders.put(orderM, new ExpectedResult(0, 5));
        orders.put(orderN, new ExpectedResult(0, 5));
        orders.put(orderO, new ExpectedResult(0, 5));
        orders.put(orderP, new ExpectedResult(0, 5));
        orders.put(orderQ, new ExpectedResult(0, 5));
        orders.put(orderR, new ExpectedResult(0, 5));
        orders.put(orderS, new ExpectedResult(0, 5));
        orders.put(orderT, new ExpectedResult(0, 5));

        return new BlisterTest(30, 200, orders);
    }

    private static BlisterTest optimize_noBlisterAvailable_allDisabled() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(null, toDate(currentDate), 10);
        OrderTest orderB = new OrderTest(null, toDate(currentDate.plusDays(1)), 10);
        OrderTest orderC = new OrderTest(null, toDate(currentDate.plusDays(2)), 20);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(0, 0));
        orders.put(orderB, new ExpectedResult(0, 0));
        orders.put(orderC, new ExpectedResult(0, 0));

        return new BlisterTest(0, 0, orders, Sets.newHashSet(orderA, orderB, orderC));
    }

    private static BlisterTest optimize_noOrderIsFulfillable_allDisabled() {
        LocalDate currentDate = LocalDate.now();
        OrderTest orderA = new OrderTest(50L, toDate(currentDate), 100);
        OrderTest orderB = new OrderTest(80L, toDate(currentDate.plusDays(1)), 10);
        OrderTest orderC = new OrderTest(100L, toDate(currentDate.plusDays(2)), 20);

        Map<OrderTest, ExpectedResult> orders = new HashMap<>();
        orders.put(orderA, new ExpectedResult(0, 0));
        orders.put(orderB, new ExpectedResult(0, 0));
        orders.put(orderC, new ExpectedResult(0, 0));

        return new BlisterTest(7, 1000, orders, Sets.newHashSet(orderA, orderB, orderC));
    }

    private static Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    }

    private static class BlisterTest {
        private final int newAmount;
        private final int oldAmount;
        private Map<OrderTest, ExpectedResult> orders;
        private final Set<OrderTest> incomplete;

        BlisterTest(int newAmount, int oldAmount, Map<OrderTest, ExpectedResult> orders) {
            this(newAmount, oldAmount, orders, Collections.emptySet());
        }

        BlisterTest(int newAmount, int oldAmount, Map<OrderTest, ExpectedResult> orders, Set<OrderTest> incomplete) {
            this.newAmount = newAmount;
            this.oldAmount = oldAmount;
            this.orders = orders;
            this.incomplete = incomplete;
        }
    }

    private static class OrderTest {
        private final Long longtime;
        private final Date orderingDate;
        private final int expectedAmount;

        OrderTest(Long longtime, Date orderingDate, int expectedAmount) {
            this.longtime = longtime;
            this.expectedAmount = expectedAmount;
            this.orderingDate = orderingDate;
        }

        @Override
        public int hashCode() {
            return Objects.hash(longtime, orderingDate, expectedAmount);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof OrderTest)) {
                return false;
            }

            OrderTest other = (OrderTest) obj;

            return Objects.equals(longtime, other.longtime) &&
                    Objects.equals(orderingDate, other.orderingDate) &&
                    Objects.equals(expectedAmount, other.expectedAmount);
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