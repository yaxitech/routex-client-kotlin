package tech.yaxi.routex.settlement

/**
 * Built-in YAXI-operated Ed25519 verifying keys used to authenticate the system version
 * returned by the TEE during key settlement. Keyed by the base64-encoded Ed25519 public
 * key.
 */
public object YaxiSystemVersionKeys {
    public val DEFAULT: Map<String, ByteArray> by lazy {
        mapOf(
            "AhrUXsV/XAvIE24RQ/Vt/zXoLodvjXoWD2fhLGuRM7U=" to
                byteArrayOf(
                    101,
                    -68,
                    76,
                    33,
                    -11,
                    -101,
                    42,
                    79,
                    -42,
                    -75,
                    125,
                    49,
                    68,
                    -123,
                    87,
                    -24,
                    123,
                    91,
                    -47,
                    21,
                    -17,
                    36,
                    -61,
                    -41,
                    82,
                    -116,
                    -96,
                    -61,
                    -20,
                    111,
                    -76,
                    -30,
                ),
            "D30zRYe8Ug9732b4Pe2BAwWAXn/T5Nss2HJOp3kLC1w=" to
                byteArrayOf(
                    8,
                    -53,
                    107,
                    97,
                    17,
                    -116,
                    16,
                    -16,
                    29,
                    -59,
                    104,
                    26,
                    -77,
                    115,
                    86,
                    2,
                    -46,
                    73,
                    107,
                    -45,
                    46,
                    34,
                    89,
                    -82,
                    117,
                    -63,
                    126,
                    -41,
                    12,
                    -123,
                    106,
                    37,
                ),
        )
    }
}
