package net.adshares.esc.qa.util;

import java.math.BigDecimal;

/**
 * ESC constants from default.hpp
 */
public class EscConst {
    /**
     * BLOCKDIV number of blocks for dividend update
     */
    public static final int BLOCK_DIVIDEND = 4;
    /**
     * BLOCKSEC block period in seconds
     */
    public static final int BLOCK_PERIOD = 32;
    /**
     * TXS_MIN_FEE minimum transfer fee
     */
    public static final BigDecimal MIN_TX_FEE = new BigDecimal("0.00000010000");
    /**
     * TXS_PUT_FEE local transfer coefficient
     */
    public static final BigDecimal LOCAL_TX_FEE_COEFFICIENT = new BigDecimal("0.0005");
    /**
     * TXS_LNG_FEE remote transfer coefficient
     */
    public static final BigDecimal REMOTE_TX_FEE_COEFFICIENT = new BigDecimal("0.0005");
    /**
     * TXS_MPT_FEE local multiple transfer coefficient
     */
    public static final BigDecimal MULTI_TX_FEE_COEFFICIENT = new BigDecimal("0.0005");
    /**
     * TXS_GET_FEE retrieve funds from remote/dead bank request fee
     */
    public static final BigDecimal RETRIEVE_REQUEST_FEE = new BigDecimal("0.00001000000");
    /**
     * TXS_GOK_FEE(x) retrieve funds from remote/dead bank fee, x is retrieved amount
     */
    public static final BigDecimal RETRIEVE_FEE = new BigDecimal("0.001");
    /**
     * TXS_BRO_FEE(x) broadcast message fee, x is message length in bytes
     */
    public static final BigDecimal BROADCAST_FEE_COEFFICIENT = new BigDecimal("0.00000050000");
    /**
     * USER_MIN_MASS minimum user balance after outgoing transfer
     */
    public static final BigDecimal USER_MIN_MASS = new BigDecimal("0.00000001000");
    /**
     * BANK_MIN_UMASS minimum bank balance after outgoing transfer
     */
    public static final BigDecimal BANK_MIN_UMASS = new BigDecimal("10.00000000000");
}
