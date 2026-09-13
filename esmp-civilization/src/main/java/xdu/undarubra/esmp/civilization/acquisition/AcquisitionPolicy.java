package xdu.undarubra.esmp.civilization.acquisition;

import xdu.undarubra.esmp.civilization.settlement.SettlementException;

import java.math.BigInteger;

public record AcquisitionPolicy(long offerTicks, int feeBasisPoints) {
    public AcquisitionPolicy {
        if (offerTicks <= 0 || feeBasisPoints <= 0)
            throw new IllegalArgumentException("Invalid acquisition policy");
    }

    public static AcquisitionPolicy defaults() {
        return new AcquisitionPolicy(36000, 1000);
    }

    public long fee(BigInteger total) {
        if (total.signum() <= 0)
            throw new SettlementException("Acquisition requires valued claimed land.");
        try {
            return total.multiply(BigInteger.valueOf(feeBasisPoints))
                    .add(BigInteger.valueOf(9999))
                    .divide(BigInteger.valueOf(10000))
                    .longValueExact();
        } catch (ArithmeticException overflow) {
            throw new SettlementException("Acquisition fee exceeds the supported integer range.");
        }
    }
}
