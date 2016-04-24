package com.paintshop;

import com.paintshop.file.InputReader;
import com.paintshop.model.Customer;
import com.paintshop.model.CustomerWish;
import com.paintshop.model.Product;
import com.paintshop.model.TestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Solver {

    public static void main(String[] args) {
        try {
            Solver solver = new Solver();
            List<String> resultLines = new ArrayList<>();
            List<TestCase> testCases = new InputReader("input.txt").getTestCases();
            for (int i = 0, j = 1; i < testCases.size(); i++, j++) {
                System.out.println("Solving testcase " + j);
                TestCase tc = testCases.get(i);
                Map<Integer, List<CustomerWish>> solutions  = solver.solve(tc);
                System.out.println("Test case has " + solutions.keySet().size() + " solution(s)");
                String result = "Case #" + j + ": ";
                if (solutions.isEmpty()) {
                    // Push output to file
                    result += "IMPOSSIBLE";
                } else {
                    Set<Product> catalog = solver.makeProductCatalog(solutions, tc.getProducts());
                    result += catalog.stream().map(p -> "" + p.getColorFinish().getCode()).collect(Collectors.joining(" "));
                }
                resultLines.add(result);
            }
            Files.write(Paths.get("output.txt"), resultLines);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Set<Product> makeProductCatalog(Map<Integer, List<CustomerWish>> solutions, List<Product> products) {
        // TreeSet to make colors ordered by their color code
        Set<Product> catalog = new TreeSet<>((o1, o2) -> Integer.valueOf(o1.getColor()).compareTo(Integer.valueOf(o2.getColor())));
        catalog.addAll(findCheapestSolution(solutions));
        // Add the colors that customers didn't request for also to the catalog
        products.forEach(p -> {
            boolean isColorPresent = catalog.stream().anyMatch(c -> p.getColor() == c.getColor());
            if (!isColorPresent) {
                catalog.add(p);
            }
        });
        return catalog;
    }

    private Set<Product> findCheapestSolution(Map<Integer, List<CustomerWish>> solutions) {
        return solutions.values().stream()
                .map(x -> new HashSet<>(wishesToProducts(x)))
                .sorted((a, b) -> costOfSolution(a).compareTo(costOfSolution(b)))
                .findFirst().get();
    }

    private List<Product> wishesToProducts(List<CustomerWish> customerWishes) {
        return customerWishes.stream().map(CustomerWish::getProduct).collect(Collectors.toList());
    }

    private Integer costOfSolution(Collection<Product> products) {
        return products.stream().map(p -> p.getColorFinish().getCode()).
                reduce(0, Integer::sum);
    }

    private Map<Integer, List<CustomerWish>> solve(TestCase testCase) {
        List<Customer> customers = new ArrayList<>(testCase.getCustomers());
        // Sort for optimal computing
        sortCustomersAndWishes(customers);
        Map<Integer, List<CustomerWish>> solutions = new HashMap<>();
        List<CustomerWish> tempGrants = new ArrayList<>();
        int solutionIndex = 0;
        // Iterate through all customers
        for (int i = 0; i < customers.size(); i++) { // Begin FOR LOOP for customers
            boolean wishGranted = false;
            // Iterate through unvisited wishes of a customer
            for (CustomerWish wish : customers.get(i).getUnVisitedWishes()) { // Begin FOR LOOP for customer wishes
                if (isGrantable(wish, tempGrants)) {
                    wish.visitAndGrant();
                    wishGranted = true;
                    tempGrants.add(wish);
                    System.out.println("Granting wish " + wish.productAsString() + " for customer " + i);
                    if (isSolved(customers)) {
                        solutions.put(solutionIndex++, new ArrayList<>(tempGrants));
                        System.out.println("Found solution number " + solutionIndex);
                        if (i > 0) {
                            // Remove the recently added wish from the list
                            removeFromGrants(wish, tempGrants);
                            int nextIndex = performResetActionsAndGetNextIndex(customers, tempGrants);
                            // System.out.println("Moving to index " + nextIndex);
                            i = nextIndex - 1;
                            break;
                        } else {
                            return solutions;
                        }
                    } else {
                        // Move to next customer
                        break;
                    }
                } else {
                    wish.visit();
                }
            } // End FOR LOOP for customer wishes

            if (!wishGranted) {
                // Reset previous successful wish grant and move back to that customer's wishes
                if (i > 0) {
                    int nextIndex = performResetActionsAndGetNextIndex(customers, tempGrants);
                    // System.out.println("Moving to index " + nextIndex);
                    i = nextIndex - 1;
                } else {
                    // Reached top of the grid - return the solutions map
                    return solutions;
                }
            }
        } // End FOR LOOP for customer wishes
        return solutions;
    }

    private int performResetActionsAndGetNextIndex(List<Customer> customers, List<CustomerWish> tempGrants) {
        int nextIndex = getFirstCustomerIndexWithLastGrantedWish(tempGrants, customers);
        // Reset all customer wishes from next index to last
        // System.out.println("Clearing visits and grants of customers from index " + (nextIndex + 1) + " onwards");
        for (int j = nextIndex + 1; j < customers.size(); j++) {
            // remove grants and clear visits completely
            customers.get(j).getGrantedWish().ifPresent(w -> {
                removeFromGrants(w, tempGrants);
            });
            customers.get(j).clearVisitsAndGrants();
        }
        customers.get(nextIndex).getGrantedWish().ifPresent(w -> {
            // Mark as visited but un-granted
            removeFromGrants(w, tempGrants);
            w.clearGrant();
            System.out.println("Un grant wish " + w.productAsString() + " for customer " + nextIndex);
        });
        return nextIndex;
    }

    private int getFirstCustomerIndexWithLastGrantedWish(List<CustomerWish> tempGrants, List<Customer> customers) {
        if (tempGrants.size() > 0) {
            CustomerWish lastGranted = tempGrants.get(tempGrants.size() - 1);
            for (int i = 0; i < customers.size(); i++) {
                Optional<CustomerWish> cwOpt = customers.get(i).getGrantedWish();
                if (cwOpt.isPresent() && cwOpt.get().isSame(lastGranted)) {
                    return i;
                }
            }
        } else {
            return 0;
        }
        return 0;
    }

    private void sortCustomersAndWishes(List<Customer> customers) {
        // Sort by customers with least preferences
        customers.sort((a, b) -> {
            if (a.getWishes().size() != b.getWishes().size()) {
                return Integer.valueOf(a.getWishes().size()).compareTo(Integer.valueOf(b.getWishes().size()));
            } else {
                return costOfSolution(wishesToProducts(a.getWishes())).compareTo(costOfSolution(wishesToProducts(b.getWishes())));
            }
        });
        // Wishes with least cost come first
        customers.forEach(c -> c.getWishes()
                .sort((a, b) ->  {
                    if (a.getColorFinish().equals(b.getColorFinish())) {
                        return Integer.valueOf(a.getColor()).compareTo(Integer.valueOf(b.getColor()));
                    } else {
                        return Integer.valueOf(a.getColorFinish().getCode())
                                .compareTo(Integer.valueOf(b.getColorFinish().getCode()));
                    }
                }));
    }

    private boolean isSolved(List<Customer> customers) {
        return customers.stream().allMatch(c -> c.hasAGrantedWish());
    }

    private void removeFromGrants(CustomerWish wish, List<CustomerWish> tempGrants) {
        tempGrants.removeIf(w -> w.isSame(wish));
    }

    private boolean isGrantable(CustomerWish wish, List<CustomerWish> tempGrant) {
        return tempGrant.stream()
                .noneMatch(w -> w.getColor() == wish.getColor() &&
                        !w.getColorFinish().equals(wish.getColorFinish()));
    }
}
