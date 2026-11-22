package uk.dx.propwatch

/**
 * A deliberately coarse, hand-simplified world outline for the station chart:
 * enough to read the continents at a glance, like a printed log card.
 * Each polygon is lon, lat pairs (equirectangular).
 */
object WorldMap {
    const val TOP = 82.0
    const val BOTTOM = -57.0

    val LAND: List<FloatArray> = listOf(
        // North America
        floatArrayOf(-168f, 66f, -162f, 70f, -140f, 70f, -125f, 70f, -110f, 73f, -95f, 72f, -85f, 70f, -80f, 73f, -72f, 68f, -62f, 60f, -56f, 52f, -60f, 47f, -66f, 45f, -70f, 42f, -74f, 40f, -76f, 35f, -81f, 31f, -80f, 25f, -82f, 27f, -84f, 30f, -90f, 30f, -94f, 29f, -97f, 27f, -97f, 21f, -95f, 18f, -91f, 19f, -90f, 21f, -87f, 21f, -88f, 16f, -84f, 15f, -83f, 10f, -79f, 9f, -77f, 8f, -80f, 7f, -83f, 8f, -86f, 11f, -92f, 14f, -97f, 16f, -105f, 20f, -106f, 23f, -110f, 23f, -112f, 29f, -114f, 31f, -117f, 32f, -120f, 34f, -124f, 40f, -124f, 46f, -125f, 49f, -130f, 54f, -136f, 58f, -142f, 60f, -150f, 61f, -154f, 58f, -160f, 58f, -162f, 60f, -166f, 62f),
        // Greenland
        floatArrayOf(-73f, 78f, -66f, 81f, -45f, 83f, -25f, 82f, -18f, 77f, -22f, 70f, -32f, 68f, -42f, 61f, -48f, 61f, -53f, 66f, -56f, 72f, -62f, 76f),
        // Iceland
        floatArrayOf(-24f, 65.5f, -19f, 66.5f, -14f, 66f, -14f, 64.5f, -19f, 63.4f, -22.5f, 64f),
        // South America
        floatArrayOf(-79f, 9f, -75f, 11f, -71f, 12f, -62f, 10.5f, -52f, 5f, -50f, 0f, -44f, -2f, -35f, -5f, -37f, -12f, -39f, -16f, -41f, -22f, -48f, -26f, -53f, -33f, -58f, -35f, -57f, -38f, -62f, -39f, -65f, -42f, -66f, -47f, -69f, -51f, -68f, -55f, -72f, -54f, -75f, -48f, -73f, -40f, -71f, -30f, -70f, -18f, -76f, -14f, -81f, -6f, -80f, -2f, -78f, 2f, -77f, 7f),
        // Eurasia
        floatArrayOf(-9.5f, 37f, -9f, 43f, -2f, 43.5f, -1.5f, 46f, -4.5f, 48f, -1f, 49.5f, 2f, 51f, 4.5f, 52.5f, 8f, 54f, 8.5f, 57f, 10.5f, 57.5f, 12f, 56f, 10.5f, 54f, 14f, 54f, 19f, 54.5f, 21f, 57f, 24f, 59.5f, 29f, 60f, 22.5f, 60.5f, 21.5f, 63f, 25f, 65.5f, 21f, 65f, 17f, 62.5f, 19f, 60f, 16f, 56.5f, 12.5f, 56f, 11f, 59f, 8f, 58f, 5f, 59f, 5f, 62f, 10f, 64f, 14f, 67.5f, 18f, 69.5f, 25f, 71f, 32f, 70f, 40f, 68f, 44f, 68f, 53f, 69f, 60f, 70f, 69f, 73f, 80f, 73f, 87f, 75f, 100f, 78f, 112f, 76f, 120f, 73f, 130f, 71f, 140f, 72f, 150f, 71f, 160f, 70f, 170f, 70f, 180f, 68.5f, 180f, 65f, 178f, 62f, 170f, 60f, 163f, 58f, 162f, 55f, 156f, 51f, 155f, 57f, 150f, 59f, 143f, 59f, 138f, 55f, 141f, 52f, 140f, 48f, 135f, 43f, 131f, 42f, 129f, 40f, 129f, 35f, 126f, 35f, 126f, 38f, 122f, 40f, 121f, 37f, 122f, 31f, 120f, 27f, 117f, 23f, 111f, 21f, 108f, 21f, 106f, 18f, 109f, 13f, 109f, 11f, 105f, 9f, 103f, 10f, 101f, 13f, 100f, 10f, 103f, 5f, 103.5f, 1.5f, 101f, 3f, 98.5f, 8f, 98f, 14f, 97f, 17f, 94f, 18f, 92f, 22f, 89f, 22f, 87f, 21f, 85f, 19.5f, 80f, 15f, 80f, 10f, 77f, 8f, 76f, 11f, 74f, 15f, 73f, 20f, 72.5f, 21.5f, 70f, 22.5f, 67f, 24.5f, 61f, 25f, 57f, 25.5f, 56.5f, 27f, 52f, 28f, 50f, 30f, 48f, 29.5f, 48.5f, 28f, 50f, 26.5f, 51f, 24.5f, 54f, 24f, 56f, 26f, 57f, 24f, 59.5f, 22.5f, 58f, 20f, 55f, 17f, 52f, 15.5f, 45f, 13f, 43f, 12.7f, 42.5f, 15f, 40f, 20f, 39f, 22f, 36.5f, 26f, 34.5f, 28f, 33f, 31f, 34.5f, 32f, 35.5f, 35.5f, 36f, 36.7f, 32f, 36.5f, 29f, 36.5f, 27f, 37.5f, 26.5f, 40f, 29f, 41f, 28f, 42.5f, 29f, 44.5f, 30.5f, 46.5f, 33f, 46f, 34f, 44.5f, 37.5f, 45f, 39f, 47f, 38f, 45f, 41.5f, 41.5f, 37f, 41f, 32f, 41.5f, 29f, 41f, 26f, 40.5f, 23f, 40f, 24f, 38f, 22f, 36.5f, 21f, 38.5f, 19.5f, 40f, 19.5f, 42f, 16f, 43.5f, 13.5f, 45.5f, 12.5f, 44f, 14f, 42f, 16f, 40f, 16.5f, 38f, 15.5f, 40f, 12f, 42f, 10f, 44f, 8.5f, 44f, 6f, 43f, 3.5f, 43.5f, 3f, 42f, 0f, 39.5f, 0f, 38.5f, -2f, 36.8f, -5.5f, 36f, -7.5f, 37f),
        // Britain
        floatArrayOf(-5.7f, 50f, -3f, 50.7f, 1.4f, 51.2f, 1.7f, 52.6f, 0.3f, 53.4f, -0.3f, 54.5f, -1.6f, 55.6f, -2.1f, 57.1f, -1.8f, 57.6f, -3.5f, 57.7f, -3f, 58.6f, -5f, 58.6f, -6.2f, 57.5f, -5.6f, 56.3f, -6.2f, 55.6f, -4.9f, 55f, -4.9f, 54.7f, -3.3f, 54.9f, -3.2f, 54f, -3f, 53.4f, -4.6f, 53.3f, -4.2f, 52.8f, -4.1f, 52.3f, -5.3f, 51.8f, -4.2f, 51.5f, -3f, 51.4f, -4.3f, 51.2f, -5.1f, 51f),
        // Ireland
        floatArrayOf(-10.3f, 51.8f, -8.2f, 51.6f, -6.2f, 52.2f, -6.1f, 53.5f, -5.5f, 54.6f, -7.4f, 55.3f, -8.6f, 54.8f, -10f, 54.2f, -9.8f, 53.4f, -10.2f, 52.5f),
        // Africa
        floatArrayOf(-17f, 21f, -17f, 14.7f, -15f, 11f, -13f, 8f, -8f, 4.5f, -2f, 5f, 5f, 6f, 8f, 4.5f, 9.5f, 3f, 9.5f, -1f, 12f, -5f, 13.5f, -11f, 12f, -18f, 15f, -27f, 18.5f, -34.5f, 22f, -34f, 26f, -33.8f, 30f, -31f, 33f, -26f, 35.5f, -24f, 35f, -20f, 40f, -15f, 40.5f, -10f, 39.5f, -5f, 42f, -1f, 46f, 2f, 51f, 10.5f, 51f, 12f, 44f, 10.5f, 43f, 12f, 39.5f, 15.5f, 37f, 19.5f, 36f, 22f, 34f, 27f, 32.5f, 30f, 30f, 31.3f, 25f, 31.7f, 20f, 30.8f, 19f, 30.3f, 15.5f, 32f, 11f, 33f, 10f, 37f, 3f, 36.8f, -1f, 35.2f, -5.5f, 35.8f, -9.5f, 32f, -10f, 29f, -13f, 27.5f),
        // Madagascar
        floatArrayOf(43.5f, -23.5f, 47f, -25.2f, 50.5f, -15.5f, 49.5f, -12f, 44f, -17f, 44f, -20.5f),
        // Sri Lanka
        floatArrayOf(79.8f, 8f, 81.8f, 7.5f, 81f, 6f, 80f, 6.2f),
        // Japan
        floatArrayOf(130f, 31.2f, 131.5f, 33.5f, 135f, 33.6f, 136.8f, 34.5f, 140f, 35f, 141f, 38f, 142f, 40.5f, 141.5f, 41.5f, 140f, 40.5f, 139.5f, 38.2f, 137f, 37f, 136f, 35.6f, 133f, 35.5f, 131f, 34.3f),
        // Hokkaido
        floatArrayOf(140f, 41.5f, 141.5f, 45.4f, 145.5f, 43.3f, 143.5f, 42f, 141f, 42.5f),
        // Sumatra
        floatArrayOf(95.3f, 5.5f, 98f, 4f, 104f, -1f, 106f, -5.8f, 103.5f, -5f, 100.5f, -1f, 98f, 2f),
        // Borneo
        floatArrayOf(109f, 1.5f, 111f, 2.8f, 115.5f, 4.8f, 117f, 7f, 119f, 5f, 118f, 1f, 117f, -1f, 116f, -4f, 111f, -3f, 110f, -1.5f),
        // Java
        floatArrayOf(105.2f, -6.8f, 112f, -6.8f, 114.5f, -7.8f, 110f, -8.1f, 106f, -7.4f),
        // Sulawesi
        floatArrayOf(119.5f, -5.5f, 119f, 0.5f, 120.5f, 1.2f, 124.8f, 1.5f, 121.5f, -1f, 122.5f, -4.5f),
        // New Guinea
        floatArrayOf(131f, -1f, 135f, -3.3f, 138f, -1.7f, 144f, -3.8f, 147.5f, -6f, 150f, -10.6f, 146f, -8.8f, 143.5f, -9f, 141f, -9.2f, 138f, -8.3f, 137f, -5f, 133f, -4f),
        // Philippines
        floatArrayOf(120f, 18.5f, 122.3f, 18.5f, 122f, 16f, 124f, 13.8f, 126f, 9.5f, 125.5f, 6.5f, 122f, 7f, 123f, 10.5f, 121f, 13.5f, 120.5f, 16f),
        // Taiwan
        floatArrayOf(120.2f, 23f, 121f, 25.2f, 121.9f, 25f, 120.8f, 22f),
        // Australia
        floatArrayOf(113.5f, -22f, 114f, -26.5f, 115f, -34f, 118f, -35f, 123f, -34f, 129f, -31.6f, 132f, -32f, 135f, -34.8f, 138f, -35.6f, 139.5f, -37.8f, 143f, -38.8f, 146f, -39f, 150f, -37.5f, 153f, -31f, 153.5f, -28f, 153f, -25f, 149f, -21f, 146f, -19f, 145.5f, -15f, 143.5f, -13.8f, 142.5f, -10.7f, 141.5f, -13f, 141.5f, -17f, 139.5f, -17.5f, 136f, -15f, 137f, -12f, 132f, -11.2f, 130f, -13f, 129f, -15f, 126f, -14f, 122f, -17f, 121f, -19f, 117f, -20.5f),
        // Tasmania
        floatArrayOf(144.6f, -40.7f, 148.3f, -40.9f, 148f, -43.2f, 146f, -43.6f),
        // NZ north
        floatArrayOf(172.7f, -34.5f, 175f, -36.5f, 178.5f, -37.7f, 176.9f, -39.6f, 175f, -41.5f, 174.6f, -39.5f, 173.8f, -39f, 174.5f, -37f),
        // NZ south
        floatArrayOf(172.7f, -40.5f, 174.3f, -41.7f, 173f, -43.8f, 171.2f, -44.5f, 169f, -46.6f, 166.5f, -46f, 168.3f, -44f, 171f, -42f),
        // Cuba
        floatArrayOf(-85f, 21.8f, -81.5f, 23.2f, -77.5f, 21.8f, -74.2f, 20.2f, -77.5f, 19.8f, -80.5f, 21.8f),
        // Hispaniola
        floatArrayOf(-74.4f, 18.4f, -72.8f, 19.9f, -69.5f, 19.7f, -68.4f, 18.6f, -71.5f, 17.8f),
        // Newfoundland
        floatArrayOf(-59.5f, 47.6f, -55.5f, 51.6f, -53f, 49.5f, -52.7f, 47f, -55.5f, 47.1f),
        // Svalbard
        floatArrayOf(11f, 78.5f, 17f, 80f, 27f, 80.2f, 22f, 78f, 16f, 76.6f),
        // Novaya Zemlya
        floatArrayOf(52f, 71.5f, 56f, 74.5f, 66f, 77f, 68f, 76.3f, 58f, 73.5f, 55f, 71f),
        // Corsica Sardinia
        floatArrayOf(8.2f, 39f, 9.6f, 39.2f, 9.8f, 41f, 9.5f, 43f, 8.6f, 42.4f, 8.4f, 40.8f),
        // Sicily
        floatArrayOf(12.4f, 38f, 15.6f, 38.3f, 15.1f, 36.7f),
        // Crete
        floatArrayOf(23.5f, 35.3f, 26.3f, 35.3f, 26f, 35f),
    )
}
