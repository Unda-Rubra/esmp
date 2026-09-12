package xdu.undarubra.esmp.civilization.settlement;

import java.math.BigInteger;
import java.util.List;

/** Integer basis points; round upward once, only after multiplying all applicable factors. */
public record ClaimPricingPolicy(
        long quoteTicks,
        List<Tier> settlements,
        List<Tier> portfolioChunks,
        List<Tier> settlementSize,
        List<Tier> proximity,
        List<Integer> earlyDiscounts,
        int activeFactor,
        int inactiveFactor) {
    public record Tier(long minimum, int basisPoints) {
        public Tier {
            if (minimum < 0 || basisPoints <= 0)
                throw new IllegalArgumentException("Invalid Claim price tier");
        }
    }

    public ClaimPricingPolicy {
        settlements = tiers(settlements);
        portfolioChunks = tiers(portfolioChunks);
        settlementSize = tiers(settlementSize);
        proximity = tiers(proximity);
        earlyDiscounts = List.copyOf(earlyDiscounts);
        if (quoteTicks <= 0
                || activeFactor <= 0
                || inactiveFactor <= 0
                || earlyDiscounts.stream().anyMatch(v -> v <= 0 || v > 10000))
            throw new IllegalArgumentException("Invalid Claim pricing policy");
    }

    private static List<Tier> tiers(List<Tier> input) {
        var result = List.copyOf(input);
        if (result.isEmpty() || result.getFirst().minimum() != 0)
            throw new IllegalArgumentException("Claim tiers must start at zero");
        long previous = -1;
        for (var tier : result) {
            if (tier.minimum() <= previous)
                throw new IllegalArgumentException("Claim tiers must increase");
            previous = tier.minimum();
        }
        return result;
    }

    public static ClaimPricingPolicy defaults() {
        return new ClaimPricingPolicy(
                1200,
                List.of(new Tier(0, 10000), new Tier(2, 12000), new Tier(4, 15000)),
                List.of(
                        new Tier(0, 10000),
                        new Tier(16, 11000),
                        new Tier(64, 13000),
                        new Tier(128, 16000)),
                List.of(new Tier(0, 10000), new Tier(8, 11000), new Tier(32, 12500)),
                List.of(
                        new Tier(0, 20000),
                        new Tier(4, 16000),
                        new Tier(8, 13000),
                        new Tier(16, 11000),
                        new Tier(32, 10000)),
                List.of(5000, 6500, 8000, 9000),
                10000,
                10500);
    }

    public static int factor(List<Tier> tiers, long value) {
        int result = tiers.getFirst().basisPoints();
        for (var tier : tiers) {
            if (value < tier.minimum()) break;
            result = tier.basisPoints();
        }
        return result;
    }

    public int early(long claimsUsed) {
        return claimsUsed > 0 && claimsUsed <= earlyDiscounts.size()
                ? earlyDiscounts.get((int) claimsUsed - 1)
                : 10000;
    }

    public long price(
            long base,
            long count,
            long area,
            int size,
            boolean active,
            boolean founding,
            long distance,
            long claimsUsed,
            boolean firstFoundingUsed) {
        if (base <= 0 || count < 0 || area < 0 || size < 0 || distance < 0 || claimsUsed < 0)
            throw new IllegalArgumentException("Invalid Claim pricing context");
        if (founding && !firstFoundingUsed) return 25;
        int[] factors = {
            factor(settlements, count),
            factor(portfolioChunks, area),
            founding ? 10000 : factor(settlementSize, size),
            founding ? 10000 : active ? activeFactor : inactiveFactor,
            founding ? factor(proximity, distance) : 10000,
            early(claimsUsed)
        };
        BigInteger numerator = BigInteger.valueOf(base), denominator = BigInteger.ONE;
        for (int factor : factors) {
            numerator = numerator.multiply(BigInteger.valueOf(factor));
            denominator = denominator.multiply(BigInteger.valueOf(10000));
        }
        try {
            return numerator
                    .add(denominator)
                    .subtract(BigInteger.ONE)
                    .divide(denominator)
                    .longValueExact();
        } catch (ArithmeticException overflow) {
            throw new SettlementException("Claim price exceeds the supported integer range");
        }
    }
}
