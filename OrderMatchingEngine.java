package com.alphalab.matchingengine;

import java.io.IOException;
import java.util.PriorityQueue;
import java.util.Scanner;

public class OrderMatchingEngine {
    private static int autoIncrementId = 0;
    private OrderBook orderBookStorage = new OrderBook();
    private OrderBook modifiedOrderBookStorage;
    private LimitOrder modifiedOrderToMatch;
    private Parser parser = new Parser();
    private OrderBookStringBuilder orderBookStringBuilder = new OrderBookStringBuilder();

    /**
     * An interface for order execution.
     */
    interface Matchable {
        void toMatchBuyOrder(Order inputOrder);

        void toMatchSellOrder(Order inputOrder);

        boolean toMatchBuyOrderQuantityLogic(OrderBook orderBookStorage, Order inputSellOrder, LimitOrder orderToMatch);

        boolean toMatchSellOrderQuantityLogic(OrderBook orderBookStorage, Order inputBuyOrder, LimitOrder orderToMatch);

    }

    /**
     * Order model to keep related information that is preserved in any type of order.
     */
    class Order {
        private String side;
        private String orderId;
        private int quantity;
        private int sortingId;

        public Order(String side, String orderId, int quantity) {
            this.side = side;
            this.orderId = orderId;
            this.quantity = quantity;
            autoIncrementId++;
            this.sortingId = autoIncrementId;
        }

        public Order(String side, String orderId, int quantity, int sortingId) {
            this.side = side;
            this.orderId = orderId;
            this.quantity = quantity;
            this.sortingId = sortingId;
        }

        public String getSide() {
            return side;
        }

        public String getOrderId() {
            return orderId;
        }

        public int getQuantity() {
            return quantity;
        }

        public int getSortingId() {
            return sortingId;
        }

        public void setSide(String side) {
            this.side = side;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public void setSortingId(int sortingId) {
            this.sortingId = sortingId;
        }

        public String execute() {
            return null;
        }
    }

    /**
     * Market order model to keep information only relevant to market order.
     */
    class MarketOrder extends Order implements Matchable {
        public MarketOrder(String side, String orderId, int quantity) {
            super(side, orderId, quantity);
        }

        @Override
        public boolean toMatchBuyOrderQuantityLogic(OrderBook orderBookStorage,
                                                    Order inputSellOrder, LimitOrder orderToMatch) {
            int currentTradeCost = orderBookStorage.getTradeCost();
            int remainingQuantity = -inputSellOrder.getQuantity() + orderToMatch.getQuantity();
            if (remainingQuantity == 0) {
                orderBookStorage.removeOrderFromBuyOrderList(orderToMatch);
                orderBookStorage.setTradeCost(currentTradeCost +
                        orderToMatch.getQuantity() * orderToMatch.getPrice());
                inputSellOrder.setQuantity(0);
                return false;
            }
            if (remainingQuantity > 0) {
                orderToMatch.setQuantity(remainingQuantity);
                orderBookStorage.setTradeCost(currentTradeCost +
                        inputSellOrder.getQuantity() * orderToMatch.getPrice());
                inputSellOrder.setQuantity(0);
                return false;
            } else {
                orderBookStorage.removeOrderFromBuyOrderList(orderToMatch);
                inputSellOrder.setQuantity(-remainingQuantity);
                orderBookStorage.setTradeCost(currentTradeCost +
                        orderToMatch.getQuantity() * orderToMatch.getPrice());
                return true;
            }
        }

        @Override
        public boolean toMatchSellOrderQuantityLogic(OrderBook orderBookStorage,
                                                     Order inputBuyOrder, LimitOrder orderToMatch) {
            int currentTradeCost = orderBookStorage.getTradeCost();
            int remainingQuantity = inputBuyOrder.getQuantity() - orderToMatch.getQuantity();
            if (remainingQuantity == 0) {
                orderBookStorage.removeOrderFromSellOrderList(orderToMatch);
                orderBookStorage.setTradeCost(currentTradeCost +
                        orderToMatch.getQuantity() * orderToMatch.getPrice());
                inputBuyOrder.setQuantity(0);
                return false;
            }
            if (remainingQuantity > 0) {
                orderBookStorage.removeOrderFromSellOrderList(orderToMatch);
                inputBuyOrder.setQuantity(remainingQuantity);
                orderBookStorage.setTradeCost(currentTradeCost +
                        orderToMatch.getQuantity() * orderToMatch.getPrice());
                return true;
            } else {
                orderToMatch.setQuantity(-remainingQuantity);
                orderBookStorage.setTradeCost(currentTradeCost +
                        inputBuyOrder.getQuantity() * orderToMatch.getPrice());
                inputBuyOrder.setQuantity(0);
                return false;
            }
        }

        @Override
        public void toMatchBuyOrder(Order inputSellOrder) {
            boolean isContinue;

            orderBookStorage.setTradeCost(0);
            modifiedOrderBookStorage = new OrderBook(orderBookStorage);
            while (!orderBookStorage.getBuyOrderList().isEmpty()) {
                LimitOrder orderToMatch = orderBookStorage.getBuyOrderList().poll();
                modifiedOrderToMatch = modifiedOrderBookStorage.getOrderByOrderId(orderToMatch.getOrderId());
                if (modifiedOrderToMatch == null) {
                    continue;
                }

                isContinue = toMatchBuyOrderQuantityLogic(modifiedOrderBookStorage,
                        inputSellOrder, modifiedOrderToMatch);

                if (!isContinue) {
                    break;
                }
            }

            orderBookStorage = modifiedOrderBookStorage;

        }

        @Override
        public void toMatchSellOrder(Order inputBuyOrder) {
            boolean isContinue;

            orderBookStorage.setTradeCost(0);
            modifiedOrderBookStorage = new OrderBook(orderBookStorage);
            while (!orderBookStorage.getSellOrderList().isEmpty()) {
                LimitOrder orderToMatch = orderBookStorage.getSellOrderList().poll();
                modifiedOrderToMatch = modifiedOrderBookStorage.getOrderByOrderId(orderToMatch.getOrderId());
                if (modifiedOrderToMatch == null) {
                    continue;
                }

                isContinue = toMatchSellOrderQuantityLogic(modifiedOrderBookStorage,
                        inputBuyOrder, modifiedOrderToMatch);
                if (!isContinue) {
                    break;
                }
            }

            orderBookStorage = modifiedOrderBookStorage;

        }

        @Override
        public String execute() {
            if (getSide().equals(Command.BUY_ORDER_COMMAND)) {
                toMatchSellOrder(this);
            } else {
                toMatchBuyOrder(this);
            }
            return "" + orderBookStorage.getTradeCost();
        }
    }

    /**
     * Limit order model to keep information only relevant to limit order.
     */
    class LimitOrder extends Order implements Matchable, Comparable<LimitOrder> {
        private int price;

        public LimitOrder(String side, String orderId, int quantity, int price) {
            super(side, orderId, quantity);
            this.price = price;
        }

        public LimitOrder(String side, String orderId, int quantity, int price, int sortingId) {
            super(side, orderId, quantity, sortingId);
            this.price = price;
        }

        public LimitOrder(LimitOrder o) {
            this(o.getSide(), o.getOrderId(), o.getQuantity(), o.getPrice(), o.getSortingId());
        }

        public int getPrice() {
            return price;
        }

        @Override
        public boolean toMatchBuyOrderQuantityLogic(OrderBook orderBookStorage,
                                                    Order inputSellOrder, LimitOrder orderToMatch) {
            int currentTradeCost = orderBookStorage.getTradeCost();
            int remainingQuantity = -inputSellOrder.getQuantity() + orderToMatch.getQuantity();
            if (remainingQuantity == 0) {
                orderBookStorage.removeOrderFromBuyOrderList(orderToMatch);
                orderBookStorage.setTradeCost(currentTradeCost +
                        orderToMatch.getQuantity() * orderToMatch.getPrice());
                inputSellOrder.setQuantity(0);
                return false;
            }
            if (remainingQuantity > 0) {
                orderToMatch.setQuantity(remainingQuantity);
                orderBookStorage.setTradeCost(currentTradeCost +
                        inputSellOrder.getQuantity() * orderToMatch.getPrice());
                inputSellOrder.setQuantity(0);
                return false;
            } else {
                orderBookStorage.removeOrderFromBuyOrderList(orderToMatch);
                inputSellOrder.setQuantity(-remainingQuantity);
                orderBookStorage.setTradeCost(currentTradeCost +
                        orderToMatch.getQuantity() * orderToMatch.getPrice());
                return true;
            }
        }

        @Override
        public boolean toMatchSellOrderQuantityLogic(OrderBook orderBookStorage,
                                                     Order inputBuyOrder, LimitOrder orderToMatch) {
            int currentTradeCost = orderBookStorage.getTradeCost();
            int remainingQuantity = inputBuyOrder.getQuantity() - orderToMatch.getQuantity();
            int buyPrice = ((LimitOrder) inputBuyOrder).getPrice();
            if (remainingQuantity == 0) {
                orderBookStorage.removeOrderFromSellOrderList(orderToMatch);
                orderBookStorage.setTradeCost(currentTradeCost + orderToMatch.getQuantity() * buyPrice);
                inputBuyOrder.setQuantity(0);
                return false;
            }
            if (remainingQuantity > 0) {
                orderBookStorage.removeOrderFromSellOrderList(orderToMatch);
                inputBuyOrder.setQuantity(remainingQuantity);
                orderBookStorage.setTradeCost(currentTradeCost + orderToMatch.getQuantity() * buyPrice);
                return true;
            } else {
                orderToMatch.setQuantity(-remainingQuantity);
                orderBookStorage.setTradeCost(currentTradeCost + inputBuyOrder.getQuantity() * buyPrice);
                inputBuyOrder.setQuantity(0);
                return false;
            }
        }

        @Override
        public void toMatchBuyOrder(Order inputSellOrder) {
            boolean isContinue;

            orderBookStorage.setTradeCost(0);
            modifiedOrderBookStorage = new OrderBook(orderBookStorage);
            while (!orderBookStorage.getBuyOrderList().isEmpty()) {
                LimitOrder orderToMatch = orderBookStorage.getBuyOrderList().poll();
                modifiedOrderToMatch = modifiedOrderBookStorage.getOrderByOrderId(orderToMatch.getOrderId());
                if (modifiedOrderToMatch == null) {
                    continue;
                }

                if (((LimitOrder) inputSellOrder).getPrice() <= orderToMatch.getPrice()) {
                    isContinue = toMatchBuyOrderQuantityLogic(modifiedOrderBookStorage,
                            inputSellOrder, modifiedOrderToMatch);
                    if (!isContinue) {
                        break;
                    }
                }
            }

            orderBookStorage = modifiedOrderBookStorage;

            if (inputSellOrder.getQuantity() > 0) {
                orderBookStorage.addOrderToOrderList((LimitOrder) inputSellOrder);
            }

        }

        @Override
        public void toMatchSellOrder(Order inputBuyOrder) {
            boolean isContinue;
            boolean isMatched = false;

            orderBookStorage.setTradeCost(0);
            modifiedOrderBookStorage = new OrderBook(orderBookStorage);
            while (!orderBookStorage.getSellOrderList().isEmpty()) {
                LimitOrder orderToMatch = orderBookStorage.getSellOrderList().poll();
                modifiedOrderToMatch = modifiedOrderBookStorage.getOrderByOrderId(orderToMatch.getOrderId());
                if (modifiedOrderToMatch == null) {
                    continue;
                }

                if (((LimitOrder) inputBuyOrder).getPrice() >= orderToMatch.getPrice()) {
                    isMatched = true;
                    isContinue = toMatchSellOrderQuantityLogic(modifiedOrderBookStorage,
                            inputBuyOrder, modifiedOrderToMatch);
                    if (!isContinue) {
                        break;
                    }
                }
            }

            orderBookStorage = modifiedOrderBookStorage;

            if (inputBuyOrder.getQuantity() > 0) {
                orderBookStorage.addOrderToOrderList((LimitOrder) inputBuyOrder);
            }

        }

        @Override
        public String execute() {
            if (getSide().equals(Command.BUY_ORDER_COMMAND)) {
                toMatchSellOrder(this);
            } else {
                toMatchBuyOrder(this);
            }
            return "" + orderBookStorage.getTradeCost();
        }

        @Override
        public int compareTo(LimitOrder otherOrder) {
            if (this.price == otherOrder.price) {
                return this.getSortingId() - otherOrder.getSortingId();
            }
            if (this.getSide().equals(Command.BUY_ORDER_COMMAND)) {
                return otherOrder.price - this.price;
            } else {
                return this.price - otherOrder.price;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }

            if (obj.getClass() != this.getClass()) {
                return false;
            }

            final LimitOrder other = (LimitOrder) obj;
            if (this.getSide().equals(other.getSide()) && this.getOrderId().equals(other.getOrderId())
                    && this.getQuantity() == (other.getQuantity()) && this.getPrice() == other.getPrice()) {
                return true;
            }

            return false;
        }

        @Override
        public String toString() {
            return this.getQuantity() + "@" + price + "#" + this.getOrderId();
        }
    }

    /**
     * IOC order model to keep information only relevant to IOC order.
     */
    class IocOrder extends LimitOrder {

        public IocOrder(String side, String orderId, int quantity, int price) {
            super(side, orderId, quantity, price);
        }

        @Override
        public void toMatchBuyOrder(Order inputSellOrder) {
            boolean isContinue;
            boolean isMatched = false;

            orderBookStorage.setTradeCost(0);
            modifiedOrderBookStorage = new OrderBook(orderBookStorage);
            while (!orderBookStorage.getBuyOrderList().isEmpty()) {
                LimitOrder orderToMatch = orderBookStorage.getBuyOrderList().poll();
                modifiedOrderToMatch = modifiedOrderBookStorage.getOrderByOrderId(orderToMatch.getOrderId());
                if (modifiedOrderToMatch == null) {
                    continue;
                }

                if (((IocOrder) inputSellOrder).getPrice() <= orderToMatch.getPrice()) {
                    isMatched = true;
                    isContinue = toMatchBuyOrderQuantityLogic(modifiedOrderBookStorage,
                            inputSellOrder, modifiedOrderToMatch);
                    if (!isContinue) {
                        break;
                    }
                }
            }

            orderBookStorage = modifiedOrderBookStorage;

        }

        @Override
        public void toMatchSellOrder(Order inputBuyOrder) {
            boolean isContinue;
            boolean isMatched = false;

            orderBookStorage.setTradeCost(0);
            modifiedOrderBookStorage = new OrderBook(orderBookStorage);
            while (!orderBookStorage.getSellOrderList().isEmpty()) {
                LimitOrder orderToMatch = orderBookStorage.getSellOrderList().poll();
                modifiedOrderToMatch = modifiedOrderBookStorage.getOrderByOrderId(orderToMatch.getOrderId());
                if (modifiedOrderToMatch == null) {
                    continue;
                }

                if (((IocOrder) inputBuyOrder).getPrice() >= orderToMatch.getPrice()) {
                    isMatched = true;
                    isContinue = toMatchSellOrderQuantityLogic(modifiedOrderBookStorage,
                            inputBuyOrder, modifiedOrderToMatch);
                    if (!isContinue) {
                        break;
                    }
                }
            }

            orderBookStorage = modifiedOrderBookStorage;

        }

        @Override
        public String execute() {
            if (getSide().equals(Command.BUY_ORDER_COMMAND)) {
                toMatchSellOrder(this);
            } else {
                toMatchBuyOrder(this);
            }
            return "" + orderBookStorage.getTradeCost();
        }
    }

    /**
     * FOK order model to keep information only relevant to FOK order.
     */
    class FokOrder extends LimitOrder {

        public FokOrder(String side, String orderId, int quantity, int price) {
            super(side, orderId, quantity, price);
        }

        @Override
        public void toMatchBuyOrder(Order inputSellOrder) {
            boolean isContinue;
            boolean isMatched = false;

            orderBookStorage.setTradeCost(0);

            if (orderBookStorage.getSatisfiedQuantitySumOfBuyOrderList(((FokOrder) inputSellOrder).getPrice())
                    < inputSellOrder.getQuantity()) {
                return;
            }

            OrderBook orderBookStorageDefensiveCopy = new OrderBook(orderBookStorage);
            modifiedOrderBookStorage = new OrderBook(orderBookStorage);
            while (!orderBookStorage.getBuyOrderList().isEmpty()) {
                LimitOrder orderToMatch = orderBookStorage.getBuyOrderList().poll();
                modifiedOrderToMatch = modifiedOrderBookStorage.getOrderByOrderId(orderToMatch.getOrderId());
                if (modifiedOrderToMatch == null) {
                    continue;
                }

                if (((FokOrder) inputSellOrder).getPrice() <= orderToMatch.getPrice()) {
                    isMatched = true;
                    isContinue = toMatchBuyOrderQuantityLogic(modifiedOrderBookStorage,
                            inputSellOrder, modifiedOrderToMatch);
                    if (!isContinue) {
                        break;
                    }
                }
            }

            if (inputSellOrder.getQuantity() == 0) {
                orderBookStorage = modifiedOrderBookStorage;
            } else {
                orderBookStorage = orderBookStorageDefensiveCopy;
            }

        }

        @Override
        public void toMatchSellOrder(Order inputBuyOrder) {
            boolean isContinue;
            boolean isMatched = false;

            if (orderBookStorage.getSatisfiedQuantitySumOfSellOrderList(((FokOrder) inputBuyOrder).getPrice())
                    < inputBuyOrder.getQuantity()) {
                return;
            }

            OrderBook orderBookStorageDefensiveCopy = new OrderBook(orderBookStorage);
            modifiedOrderBookStorage = new OrderBook(orderBookStorage);
            while (!orderBookStorage.getSellOrderList().isEmpty()) {
                LimitOrder orderToMatch = orderBookStorage.getSellOrderList().poll();
                modifiedOrderToMatch = modifiedOrderBookStorage.getOrderByOrderId(orderToMatch.getOrderId());
                if (modifiedOrderToMatch == null) {
                    continue;
                }

                if (((FokOrder) inputBuyOrder).getPrice() >= orderToMatch.getPrice()) {
                    isMatched = true;
                    isContinue = toMatchSellOrderQuantityLogic(modifiedOrderBookStorage,
                            inputBuyOrder, modifiedOrderToMatch);
                    if (!isContinue) {
                        break;
                    }
                }
            }

            if (inputBuyOrder.getQuantity() == 0) {
                orderBookStorage = modifiedOrderBookStorage;
            } else {
                orderBookStorage = orderBookStorageDefensiveCopy;
            }

        }

        @Override
        public String execute() {
            if (getSide().equals(Command.BUY_ORDER_COMMAND)) {
                toMatchSellOrder(this);
            } else {
                toMatchBuyOrder(this);
            }
            return "" + orderBookStorage.getTradeCost();
        }


    }

    /**
     * Utility exit order for exiting purpose.
     */
    class ExitOrder extends Order {
        public ExitOrder(String side, String orderId, int quantity) {
            super(side, orderId, quantity);
        }
    }

    /**
     * Utility cancel order for cancelling purpose.
     */
    class CancelOrder extends Order {
        public CancelOrder(String side, String orderId, int quantity) {
            super(side, orderId, quantity);
        }

        public String execute() {
            orderBookStorage.removeOrderByOrderId(this.getOrderId());
            return null;
        }
    }

    /**
     * Utility replace order for replacing purpose.
     */
    class ReplaceOrder extends Order {
        private int price;

        public ReplaceOrder(String side, String orderId, int quantity, int price) {
            super(side, orderId, quantity);
            this.price = price;
        }

        public String execute() {
            orderBookStorage.replaceOrder(this.getOrderId(), this.getQuantity(), price);
            return "";
        }
    }

    class IcebergOrder extends LimitOrder {
        private int displaySize;
        private int totalQuantity;

        public IcebergOrder(String side, String orderId, int totalQuantity, int price, int displaySize) {
            super(side, orderId, Math.min(totalQuantity, displaySize), price);
            this.displaySize = displaySize;
            this.totalQuantity = totalQuantity;
        }

        public IcebergOrder(IcebergOrder o) {
            this(o.getSide(),o.getOrderId(),o.getTotalQuantity(),o.totalQuantity,o.displaySize);
        }


        public int getDisplaySize() {
            return displaySize;
        }

        public int getTotalQuantity() {
            return totalQuantity;
        }

        public void setDisplaySize(int displaySize) {
            this.displaySize = displaySize;
        }

        public void setTotalQuantity(int totalQuantity) {
            this.totalQuantity = totalQuantity;
        }

        public void toMatchBuyOrder(IcebergOrder inputSellOrder) {
            orderBookStorage.setTradeCost(0);
            modifiedOrderBookStorage = new OrderBook(orderBookStorage);
            while (!orderBookStorage.getBuyOrderList().isEmpty()) {
                LimitOrder orderToMatch = orderBookStorage.getBuyOrderList().poll();
                modifiedOrderToMatch = modifiedOrderBookStorage.getOrderByOrderId(orderToMatch.getOrderId());
                if (modifiedOrderToMatch == null) {
                    continue;
                }

                if (inputSellOrder.getPrice() <= orderToMatch.getPrice()) {
                    int initialQuantity = inputSellOrder.getQuantity();
                    toMatchBuyOrderQuantityLogic(modifiedOrderBookStorage,
                            inputSellOrder, modifiedOrderToMatch);
                    inputSellOrder.setTotalQuantity(inputSellOrder.getTotalQuantity() -
                            (initialQuantity - inputSellOrder.getQuantity()));
                    modifiedOrderBookStorage.addOrderToOrderList(new IcebergOrder(inputSellOrder));
                    break;
                }
            }
            orderBookStorage = modifiedOrderBookStorage;
        }

        public void toMatchSellOrder(IcebergOrder inputBuyOrder) {
            orderBookStorage.setTradeCost(0);
            modifiedOrderBookStorage = new OrderBook(orderBookStorage);
            while (!orderBookStorage.getBuyOrderList().isEmpty()) {
                LimitOrder orderToMatch = orderBookStorage.getSellOrderList().poll();
                modifiedOrderToMatch = modifiedOrderBookStorage.getOrderByOrderId(orderToMatch.getOrderId());
                if (modifiedOrderToMatch == null) {
                    continue;
                }

                if (inputBuyOrder.getPrice() >= orderToMatch.getPrice()) {
                    int initialQuantity = inputBuyOrder.getQuantity();
                    toMatchBuyOrderQuantityLogic(modifiedOrderBookStorage,
                            inputBuyOrder, modifiedOrderToMatch);
                    inputBuyOrder.setTotalQuantity(inputBuyOrder.getTotalQuantity() -
                            (initialQuantity - inputBuyOrder.getQuantity()));
                    modifiedOrderBookStorage.addOrderToOrderList(new IcebergOrder(inputBuyOrder));
                    break;
                }
            }
            orderBookStorage = modifiedOrderBookStorage;
        }

        @Override
        public String execute() {
            if (getSide().equals(Command.BUY_ORDER_COMMAND)) {
                toMatchSellOrder(this);
            } else {
                toMatchBuyOrder(this);
            }
            return "" + orderBookStorage.getTradeCost();
        }

        @Override
        public String toString() {
            return this.getQuantity() + "(" + this.getTotalQuantity() + ")"
                    + "@" + this.getPrice() + "#" + this.getOrderId();
        }

    }

    /**
     * OrderBook to store buy and sell orders in two lists.
     */
    class OrderBook {
        private PriorityQueue<LimitOrder> buyOrderList;
        private PriorityQueue<LimitOrder> sellOrderList;
        private int tradeCost;

        public OrderBook() {
            this.buyOrderList = new PriorityQueue<>();
            this.sellOrderList = new PriorityQueue<>();
        }

        /**
         * Defensive copy.
         */
        public OrderBook(OrderBook ob) {
            this.buyOrderList = new PriorityQueue<>(ob.buyOrderList);
            this.sellOrderList = new PriorityQueue<>(ob.sellOrderList);
            this.tradeCost = ob.tradeCost;
        }

        public int getTradeCost() {
            return tradeCost;
        }

        public void setTradeCost(int tradeCost) {
            this.tradeCost = tradeCost;
        }

        public PriorityQueue<LimitOrder> getBuyOrderList() {
            return buyOrderList;
        }

        public PriorityQueue<LimitOrder> getSellOrderList() {
            return sellOrderList;
        }

        public int getSatisfiedQuantitySumOfBuyOrderList(int price) {
            int sum = 0;
            for (LimitOrder order : buyOrderList) {
                if (price <= order.getPrice()) {
                    sum += order.getQuantity();
                }
            }
            return sum;
        }

        public int getSatisfiedQuantitySumOfSellOrderList(int price) {
            int sum = 0;
            for (LimitOrder order : sellOrderList) {
                if (price >= order.getPrice()) {
                    sum += order.getQuantity();
                }
            }
            return sum;
        }

        public void removeOrderFromBuyOrderList(Order order) {
            buyOrderList.remove(order);
        }

        public void removeOrderFromSellOrderList(Order order) {
            sellOrderList.remove(order);
        }

        public void removeOrderByOrderId(String orderId) {
            PriorityQueue<LimitOrder> buyOrderListDefensiveCopy = new PriorityQueue<>(buyOrderList);
            PriorityQueue<LimitOrder> sellOrderListDefensiveCopy = new PriorityQueue<>(sellOrderList);
            for (LimitOrder buyOrder : buyOrderListDefensiveCopy) {
                if (orderId.equals(buyOrder.getOrderId())) {
                    buyOrderList.remove(buyOrder);
                    break;
                }
            }

            for (LimitOrder sellOrder : sellOrderListDefensiveCopy) {
                if (orderId.equals(sellOrder.getOrderId())) {
                    sellOrderList.remove(sellOrder);
                    break;
                }
            }
        }

        public LimitOrder getOrderByOrderId(String orderId) {
            for (LimitOrder buyOrder : buyOrderList) {
                if (orderId.equals(buyOrder.getOrderId())) {
                    return buyOrder;
                }
            }

            for (LimitOrder sellOrder : sellOrderList) {
                if (orderId.equals(sellOrder.getOrderId())) {
                    return sellOrder;
                }
            }

            return null;
        }

        public void replaceOrder(String orderId, int quantity, int price) {
            LimitOrder orderToChange = getOrderByOrderId(orderId);
            if (orderToChange == null) {
                return;
            }
            int oldQuantity = orderToChange.getQuantity();
            int oldPrice = orderToChange.getPrice();

            if (oldPrice == price && quantity <= oldQuantity) {
                orderToChange.setQuantity(quantity);
            } else {
                LimitOrder oldOrder = new LimitOrder(orderToChange);
                orderBookStorage.removeOrderByOrderId(orderId);
                orderBookStorage.addOrderToOrderList(new LimitOrder(oldOrder.getSide(), orderId, quantity, price));
            }
        }

        public void addOrderToOrderList(LimitOrder order) {
            String orderSide = order.getSide();

            if (orderSide.equals(Command.BUY_ORDER_COMMAND)) {
                buyOrderList.add(order);
            } else {
                sellOrderList.add(order);
            }
        }
    }

    /**
     * Utility class for command names.
     */
    static class Command {
        static final String SUBMIT_COMMAND = "SUB";
        static final String CANCEL_COMMAND = "CXL";
        static final String REPLACE_COMMAND = "CRP";
        static final String END_COMMAND = "END";
        static final String LIMIT_ORDER_COMMAND = "LO";
        static final String MARKET_ORDER_COMMAND = "MO";
        static final String IOC_ORDER_COMMAND = "IOC";
        static final String FOK_ORDER_COMMAND = "FOK";
        static final String BUY_ORDER_COMMAND = "B";
        static final String SELL_ORDER_COMMAND = "S";
        static final String ICE_ORDER_COMMAND = "ICE";
    }

    /**
     * Parses the input to return order object depends on the input command word.
     */
    class Parser {
        private String[] inputWords;
        private String commandWord;
        private String orderType;
        private String side;
        private String orderId;
        private int quantity;
        private int price;

        private boolean NOT_REPLACED_ORDER = false;

        public Order parse(String input) throws IOException {
            inputWords = input.trim().split(" ");
            // assumes that input is always valid, hence no exception is expected to be thrown
            commandWord = inputWords[0];

            switch (commandWord) {

            case Command.SUBMIT_COMMAND:
                orderType = inputWords[1];
                side = inputWords[2];
                orderId = inputWords[3];
                quantity = Integer.parseInt(inputWords[4]);

                switch (orderType) {

                case Command.LIMIT_ORDER_COMMAND:
                    price = Integer.parseInt(inputWords[5]);
                    return new LimitOrder(side, orderId, quantity, price);

                case Command.MARKET_ORDER_COMMAND:
                    return new MarketOrder(side, orderId, quantity);

                case Command.IOC_ORDER_COMMAND:
                    price = Integer.parseInt(inputWords[5]);
                    return new IocOrder(side, orderId, quantity, price);

                case Command.FOK_ORDER_COMMAND:
                    price = Integer.parseInt(inputWords[5]);
                    return new FokOrder(side, orderId, quantity, price);

                case Command.ICE_ORDER_COMMAND:
                    price = Integer.parseInt(inputWords[5]);
                    // not finished
                }

            case Command.CANCEL_COMMAND:
                return new CancelOrder("", inputWords[1], 0);

            case Command.REPLACE_COMMAND:
                orderId = inputWords[1];
                quantity = Integer.parseInt(inputWords[2]);
                price = Integer.parseInt(inputWords[3]);
                return new ReplaceOrder("", orderId, quantity, price);

            case Command.END_COMMAND:
                return new ExitOrder("", "", 0);

            default:
                throw new IOException("Unknown input!");
            }
        }
    }

    /**
     * Builds string output of the whole OrderBook.
     */
    class OrderBookStringBuilder {
        public String build() {
            StringBuilder sb = new StringBuilder();
            sb.append("B: ");
            while (!orderBookStorage.buyOrderList.isEmpty()) {
                sb.append(orderBookStorage.buyOrderList.poll() + " ");
            }
            sb.append("\nS: ");
            while (!orderBookStorage.sellOrderList.isEmpty()) {
                sb.append(orderBookStorage.sellOrderList.poll() + " ");
            }
            return sb.toString();
        }
    }

    public static void main(String[] args) throws IOException {
        OrderMatchingEngine engine = new OrderMatchingEngine();
        Order parsedOrder;
        String output;
        boolean isEnd = false;

        Scanner sc = new Scanner(System.in);

        while (sc.hasNext() && !isEnd) {
            String input = sc.nextLine();
            parsedOrder = engine.parser.parse(input);
            if (parsedOrder instanceof ExitOrder) {
                output = engine.orderBookStringBuilder.build();
                isEnd = true;
            } else {
                output = parsedOrder.execute();
            }
            if (output != null) {
                System.out.println(output);
            }
        }
    }
}
