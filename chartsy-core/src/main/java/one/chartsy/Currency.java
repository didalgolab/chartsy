/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import one.chartsy.money.AbstractCurrencyRegistry.EnumEnclosed;
import one.chartsy.money.CurrencyRegistry;
import org.openide.util.lookup.ServiceProvider;

/**
 * A unit of currency.  Such as the British Pound, Euro, US Dollar, Bitcoin or other.
 *
 */
public interface Currency {

    String currencyCode();

    String currencyName();

    int numericCode();

    int defaultFractionDigits();

    static Currency of(String currencyCode) {
        return Currencies.getCurrency(currencyCode);
    }

    /* The selection of commonly-used stable currencies. */
    Currency AUD = ISO4217.AUD;
    Currency CAD = ISO4217.CAD;
    Currency CHF = ISO4217.CHF;
    Currency EUR = ISO4217.EUR;
    Currency GBP = ISO4217.GBP;
    Currency JPY = ISO4217.JPY;
    Currency NZD = ISO4217.NZD;
    Currency USD = ISO4217.USD;
    Currency BTC = Crypto.BTC;

    default String numericCodeAsString() {
        int numericCode = numericCode();
        if (numericCode < 0 || numericCode > 999)
            return "";

        StringBuilder buf = new StringBuilder();
        if (numericCode < 100)  buf.append('0');
        if (numericCode < 10)   buf.append('0');
        return buf.append(numericCode).toString();
    }

    enum ISO4217 implements Currency {
        AED("UAE Dirham", 784, 2),
        AFN("Afghani", 971, 2),
        ALL("Lek", 8, 2),
        AMD("Armenian Dram", 51, 2),
        ANG("Netherlands Antillean Guilder", 532, 2),
        AOA("Kwanza", 973, 2),
        ARS("Argentine Peso", 32, 2),
        AUD("Australian Dollar", 36, 2),
        AWG("Aruban Florin", 533, 2),
        AZN("Azerbaijan Manat", 944, 2),
        BAM("Convertible Mark", 977, 2),
        BBD("Barbados Dollar", 52, 2),
        BDT("Taka", 50, 2),
        BGN("Bulgarian Lev", 975, 2),
        BHD("Bahraini Dinar", 48, 3),
        BIF("Burundi Franc", 108, 0),
        BMD("Bermudian Dollar", 60, 2),
        BND("Brunei Dollar", 96, 2),
        BOB("Boliviano", 68, 2),
        BOV("Mvdol", 984, 2),
        BRL("Brazilian Real", 986, 2),
        BSD("Bahamian Dollar", 44, 2),
        BTN("Ngultrum", 64, 2),
        BWP("Pula", 72, 2),
        BYN("Belarusian Ruble", 933, 2),
        BZD("Belize Dollar", 84, 2),
        CAD("Canadian Dollar", 124, 2),
        CDF("Congolese Franc", 976, 2),
        CHE("WIR Euro", 947, 2),
        CHF("Swiss Franc", 756, 2),
        CHW("WIR Franc", 948, 2),
        CLF("Unidad de Fomento", 990, 4),
        CLP("Chilean Peso", 152, 0),
        CNY("Yuan Renminbi", 156, 2),
        COP("Colombian Peso", 170, 2),
        COU("Unidad de Valor Real", 970, 2),
        CRC("Costa Rican Colon", 188, 2),
        CUC("Peso Convertible", 931, 2),
        CUP("Cuban Peso", 192, 2),
        CVE("Cabo Verde Escudo", 132, 2),
        CZK("Czech Koruna", 203, 2),
        DJF("Djibouti Franc", 262, 0),
        DKK("Danish Krone", 208, 2),
        DOP("Dominican Peso", 214, 2),
        DZD("Algerian Dinar", 12, 2),
        EGP("Egyptian Pound", 818, 2),
        ERN("Nakfa", 232, 2),
        ETB("Ethiopian Birr", 230, 2),
        EUR("Euro", 978, 2),
        FJD("Fiji Dollar", 242, 2),
        FKP("Falkland Islands Pound", 238, 2),
        GBP("Pound Sterling", 826, 2),
        GEL("Lari", 981, 2),
        GHS("Ghana Cedi", 936, 2),
        GIP("Gibraltar Pound", 292, 2),
        GMD("Dalasi", 270, 2),
        GNF("Guinean Franc", 324, 0),
        GTQ("Quetzal", 320, 2),
        GYD("Guyana Dollar", 328, 2),
        HKD("Hong Kong Dollar", 344, 2),
        HNL("Lempira", 340, 2),
        HRK("Kuna", 191, 2),
        HTG("Gourde", 332, 2),
        HUF("Forint", 348, 2),
        IDR("Rupiah", 360, 2),
        ILS("New Israeli Sheqel", 376, 2),
        INR("Indian Rupee", 356, 2),
        IQD("Iraqi Dinar", 368, 3),
        IRR("Iranian Rial", 364, 2),
        ISK("Iceland Krona", 352, 0),
        JMD("Jamaican Dollar", 388, 2),
        JOD("Jordanian Dinar", 400, 3),
        JPY("Yen", 392, 0),
        KES("Kenyan Shilling", 404, 2),
        KGS("Som", 417, 2),
        KHR("Riel", 116, 2),
        KMF("Comorian Franc ", 174, 0),
        KPW("North Korean Won", 408, 2),
        KRW("Won", 410, 0),
        KWD("Kuwaiti Dinar", 414, 3),
        KYD("Cayman Islands Dollar", 136, 2),
        KZT("Tenge", 398, 2),
        LAK("Lao Kip", 418, 2),
        LBP("Lebanese Pound", 422, 2),
        LKR("Sri Lanka Rupee", 144, 2),
        LRD("Liberian Dollar", 430, 2),
        LSL("Loti", 426, 2),
        LYD("Libyan Dinar", 434, 3),
        MAD("Moroccan Dirham", 504, 2),
        MDL("Moldovan Leu", 498, 2),
        MGA("Malagasy Ariary", 969, 2),
        MKD("Denar", 807, 2),
        MMK("Kyat", 104, 2),
        MNT("Tugrik", 496, 2),
        MOP("Pataca", 446, 2),
        MRU("Ouguiya", 929, 2),
        MUR("Mauritius Rupee", 480, 2),
        MVR("Rufiyaa", 462, 2),
        MWK("Malawi Kwacha", 454, 2),
        MXN("Mexican Peso", 484, 2),
        MXV("Mexican Unidad de Inversion (UDI)", 979, 2),
        MYR("Malaysian Ringgit", 458, 2),
        MZN("Mozambique Metical", 943, 2),
        NAD("Namibia Dollar", 516, 2),
        NGN("Naira", 566, 2),
        NIO("Cordoba Oro", 558, 2),
        NOK("Norwegian Krone", 578, 2),
        NPR("Nepalese Rupee", 524, 2),
        NZD("New Zealand Dollar", 554, 2),
        OMR("Rial Omani", 512, 3),
        PAB("Balboa", 590, 2),
        PEN("Sol", 604, 2),
        PGK("Kina", 598, 2),
        PHP("Philippine Peso", 608, 2),
        PKR("Pakistan Rupee", 586, 2),
        PLN("Zloty", 985, 2),
        PYG("Guarani", 600, 0),
        QAR("Qatari Rial", 634, 2),
        RON("Romanian Leu", 946, 2),
        RSD("Serbian Dinar", 941, 2),
        RUB("Russian Ruble", 643, 2),
        RWF("Rwanda Franc", 646, 0),
        SAR("Saudi Riyal", 682, 2),
        SBD("Solomon Islands Dollar", 90, 2),
        SCR("Seychelles Rupee", 690, 2),
        SDG("Sudanese Pound", 938, 2),
        SEK("Swedish Krona", 752, 2),
        SGD("Singapore Dollar", 702, 2),
        SHP("Saint Helena Pound", 654, 2),
        SLL("Leone", 694, 2),
        SOS("Somali Shilling", 706, 2),
        SRD("Surinam Dollar", 968, 2),
        SSP("South Sudanese Pound", 728, 2),
        STN("Dobra", 930, 2),
        SVC("El Salvador Colon", 222, 2),
        SYP("Syrian Pound", 760, 2),
        SZL("Lilangeni", 748, 2),
        THB("Baht", 764, 2),
        TJS("Somoni", 972, 2),
        TMT("Turkmenistan New Manat", 934, 2),
        TND("Tunisian Dinar", 788, 3),
        TOP("Pa’anga", 776, 2),
        TRY("Turkish Lira", 949, 2),
        TTD("Trinidad and Tobago Dollar", 780, 2),
        TWD("New Taiwan Dollar", 901, 2),
        TZS("Tanzanian Shilling", 834, 2),
        UAH("Hryvnia", 980, 2),
        UGX("Uganda Shilling", 800, 0),
        USD("US Dollar", 840, 2),
        USN("US Dollar (Next day)", 997, 2),
        UYI("Uruguay Peso en Unidades Indexadas (UI)", 940, 0),
        UYU("Peso Uruguayo", 858, 2),
        UYW("Unidad Previsional", 927, 4),
        UZS("Uzbekistan Sum", 860, 2),
        VED("Bolívar Soberano", 926, 2),
        VES("Bolívar Soberano", 928, 2),
        VND("Dong", 704, 0),
        VUV("Vatu", 548, 0),
        WST("Tala", 882, 2),
        XAF("CFA Franc BEAC", 950, 0),
        XAG("Silver", 961),
        XAU("Gold", 959),
        XBA("Bond Markets Unit European Composite Unit (EURCO)", 955),
        XBB("Bond Markets Unit European Monetary Unit (E.M.U.-6)", 956),
        XBC("Bond Markets Unit European Unit of Account 9 (E.U.A.-9)", 957),
        XBD("Bond Markets Unit European Unit of Account 17 (E.U.A.-17)", 958),
        XCD("East Caribbean Dollar", 951, 2),
        XDR("SDR (Special Drawing Right)", 960),
        XOF("CFA Franc BCEAO", 952, 0),
        XPD("Palladium", 964),
        XPF("CFP Franc", 953, 0),
        XPT("Platinum", 962),
        XSU("Sucre", 994),
        XTS("Test", 963),
        XUA("ADB Unit of Account", 965),
        XXX("None", 999),
        YER("Yemeni Rial", 886, 2),
        ZAR("Rand", 710, 2),
        ZMW("Zambian Kwacha", 967, 2),
        ZWL("Zimbabwe Dollar", 932, 2);

        private final String name;
        private final int numericCode;
        private final Integer defaultFractionDigits;

        ISO4217(String name, int numericCode) {
            this(name, numericCode, -1);
        }

        ISO4217(String name, int numericCode, int defaultFractionDigits) {
            this.name = name;
            this.numericCode = numericCode;
            this.defaultFractionDigits = defaultFractionDigits;
        }

        @Override
        public String currencyCode() {
            return name();
        }

        @Override
        public String currencyName() {
            return name;
        }

        @Override
        public int numericCode() {
            return numericCode;
        }

        @Override
        public int defaultFractionDigits() {
            return defaultFractionDigits;
        }

        @ServiceProvider(service = CurrencyRegistry.class)
        public static class Registry extends EnumEnclosed { }
    }

    enum Crypto implements Currency {
        BTC("Bitcoin", 8);

        @Override
        public String currencyCode() {
            return name();
        }

        @Override
        public String currencyName() {
            return name;
        }

        @Override
        public int numericCode() {
            return 0;
        }

        @Override
        public int defaultFractionDigits() {
            return defaultFractionDigits;
        }

        Crypto(String name, int defaultFractionDigits) {
            this.name = name;
            this.defaultFractionDigits = defaultFractionDigits;
        }

        private final String name;
        private final int defaultFractionDigits;

        @ServiceProvider(service = CurrencyRegistry.class)
        public static class Registry extends EnumEnclosed { }
    }

    record Unit(String currencyCode, String currencyName, int numericCode, int defaultFractionDigits) implements Currency {
        public Unit(String currencyCode, String currencyName) {
            this(currencyCode, currencyName, -1, -1);
        }
    }
}
