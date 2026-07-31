package com.ingesta.service;

import com.ingesta.config.ScoringProperties;
import com.ingesta.model.RuleActivation;
import com.ingesta.model.Transaction;
import com.ingesta.model.TransactionScore;
import com.ingesta.testsupport.TransaccionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.ingesta.testsupport.TransaccionFixture.T0;
import static com.ingesta.testsupport.TransaccionFixture.una;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Banco de pruebas del motor de scoring.
 *
 * Es un test unitario puro: construye el motor a mano, sin contexto de Spring y
 * sin credenciales de Azure. Esto es deliberado -- los tests previos del motor
 * eran @SpringBootTest que ingestaban por HTTP, y por ese camino los instantes
 * los fija el servidor, asi que las fronteras de las reglas temporales
 * (velocidad y geo) eran imposibles de controlar: las tres transacciones se
 * ingestaban con milisegundos de diferencia y las reglas se activaban por
 * razones distintas de las que el test pretendia comprobar.
 *
 * Puntos por regla: VELOCIDAD 35, MONTO_ATIPICO 30, GEO_IMPOSIBLE 17,
 * COMERCIO_RIESGO 20. Umbral 60, y se abre caso solo si score > umbral.
 */
class TransactionScoringEngineTest {

    private static final String VELOCIDAD = "VELOCIDAD";
    private static final String MONTO = "MONTO_ATIPICO";
    private static final String GEO = "GEO_IMPOSIBLE";
    private static final String COMERCIO = "COMERCIO_RIESGO";

    // Bogota y Madrid: 8011.66 km de separacion.
    private static final double BOGOTA_LAT = 4.7110;
    private static final double BOGOTA_LON = -74.0721;
    private static final double MADRID_LAT = 40.4168;
    private static final double MADRID_LON = -3.7038;

    private ScoringProperties propiedades;
    private TransactionScoringEngine motor;

    @BeforeEach
    void prepararMotorConLaConfiguracionDeProduccion() {
        propiedades = new ScoringProperties();
        // Los defaults del bean ya coinciden con application.properties; solo la
        // lista de categorias de riesgo llega vacia por defecto.
        propiedades.setRiskMerchantCategories(List.of("gambling", "crypto", "adult"));
        motor = new TransactionScoringEngine(propiedades, Clock.fixed(T0, ZoneOffset.UTC));
    }

    private TransactionScore puntuar(Transaction transaccion, Transaction... historial) {
        return motor.score(transaccion, List.of(historial));
    }

    private boolean activo(TransactionScore score, String reglaId) {
        return score.activations().stream().anyMatch(a -> a.ruleId().equals(reglaId));
    }

    private List<String> detallesDe(TransactionScore score, String reglaId) {
        return score.activations().stream()
                .filter(a -> a.ruleId().equals(reglaId))
                .findFirst()
                .orElseThrow()
                .details();
    }

    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Regla VELOCIDAD")
    class Velocidad {

        @Test
        void noSeActivaConHistorialVacio() {
            TransactionScore score = puntuar(una().enT0MasSegundos(180).construir());

            assertFalse(activo(score, VELOCIDAD));
            assertEquals(0, score.score());
        }

        @Test
        void noSeActivaConUnaSolaTransaccionPrevia() {
            // La actual cuenta como 1, mas 1 previa = 2, por debajo del minimo de 3.
            TransactionScore score = puntuar(
                    una().conId("actual").enT0MasSegundos(180).construir(),
                    una().conId("previa").enT0MasSegundos(120).construir());

            assertFalse(activo(score, VELOCIDAD));
        }

        @Test
        void seActivaJustoAlAlcanzarElMinimoDeTres() {
            TransactionScore score = puntuar(
                    una().conId("actual").enT0MasSegundos(180).construir(),
                    una().conId("p1").enT0MasSegundos(60).construir(),
                    una().conId("p2").enT0MasSegundos(120).construir());

            assertTrue(activo(score, VELOCIDAD));
            assertEquals(35, score.score());
            assertTrue(detallesDe(score, VELOCIDAD).contains("transactionsInWindow=3"));
        }

        @Test
        void laTransaccionExactamenteEnElBordeDeLaVentanaSiCuenta() {
            // Ventana = [actual - 3min, +inf). El borde es inclusivo (!isBefore).
            Instant actual = T0.plusSeconds(180);
            TransactionScore score = puntuar(
                    una().conId("actual").en(actual).construir(),
                    una().conId("borde").en(actual.minusSeconds(180)).construir(),
                    una().conId("dentro").enT0MasSegundos(120).construir());

            assertTrue(activo(score, VELOCIDAD), "el instante exacto del borde debe contar");
        }

        @Test
        void unNanosegundoAntesDelBordeNoCuenta() {
            Instant actual = T0.plusSeconds(180);
            TransactionScore score = puntuar(
                    una().conId("actual").en(actual).construir(),
                    una().conId("fuera").en(actual.minusSeconds(180).minusNanos(1)).construir(),
                    una().conId("dentro").enT0MasSegundos(120).construir());

            assertFalse(activo(score, VELOCIDAD), "un nanosegundo antes del borde queda fuera");
        }

        @Test
        void lasTransaccionesFueraDeLaVentanaNoCuentan() {
            TransactionScore score = puntuar(
                    una().conId("actual").enT0MasSegundos(180).construir(),
                    una().conId("vieja1").enT0MasSegundos(-600).construir(),
                    una().conId("vieja2").enT0MasSegundos(-300).construir(),
                    una().conId("reciente").enT0MasSegundos(120).construir());

            assertFalse(activo(score, VELOCIDAD));
        }

        @Test
        void variasTransaccionesEnElMismoInstanteSeCuentanTodas() {
            TransactionScore score = puntuar(
                    una().conId("actual").en(T0).construir(),
                    una().conId("a").en(T0).construir(),
                    una().conId("b").en(T0).construir());

            assertTrue(activo(score, VELOCIDAD));
            assertTrue(detallesDe(score, VELOCIDAD).contains("transactionsInWindow=3"));
        }

        @Test
        void laVentanaNoTieneCotaSuperiorYCuentaTransaccionesPosteriores() {
            // Documenta un comportamiento real: el filtro solo mira el inicio de la
            // ventana, asi que transacciones ingestadas DESPUES de la actual elevan
            // su score. Ocurre de verdad porque el scoring es asincrono.
            TransactionScore score = puntuar(
                    una().conId("actual").enT0MasSegundos(180).construir(),
                    una().conId("futura1").enT0MasSegundos(240).construir(),
                    una().conId("futura2").enT0MasSegundos(300).construir());

            assertTrue(activo(score, VELOCIDAD),
                    "hoy las transacciones posteriores cuentan; si se acota la ventana por arriba, este test cambia");
        }
    }

    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Regla MONTO_ATIPICO")
    class Monto {

        @Test
        void noSeActivaConHistorialVacioAunqueElMontoSeaEnorme() {
            TransactionScore score = puntuar(una().conMonto("999999.00").construir());

            assertFalse(activo(score, MONTO));
            assertEquals(0, score.score());
        }

        @Test
        void noSeActivaJustoPorDebajoDelUmbral() {
            // Promedio 100.00 -> umbral 5x = 500.00
            TransactionScore score = puntuar(
                    una().conId("actual").conMonto("499.99").construir(),
                    una().conId("p1").conMonto("100.00").construir());

            assertFalse(activo(score, MONTO));
        }

        @Test
        void noSeActivaExactamenteEnElUmbral() {
            // La comparacion es estricta (> 0), asi que 5x exacto NO activa.
            TransactionScore score = puntuar(
                    una().conId("actual").conMonto("500.00").construir(),
                    una().conId("p1").conMonto("100.00").construir());

            assertFalse(activo(score, MONTO), "el umbral exacto no debe activar la regla");
        }

        @Test
        void seActivaUnCentimoPorEncimaDelUmbral() {
            TransactionScore score = puntuar(
                    una().conId("actual").conMonto("500.01").construir(),
                    una().conId("p1").conMonto("100.00").construir());

            assertTrue(activo(score, MONTO));
            assertEquals(30, score.score());
        }

        @Test
        void laEscalaDecimalNoAlteraLaComparacionEnElUmbral() {
            // 500.000000 y 500.00 son el mismo numero: compareTo ignora la escala.
            TransactionScore score = puntuar(
                    una().conId("actual").conMonto("500.000000").construir(),
                    una().conId("p1").conMonto("100.00").construir());

            assertFalse(activo(score, MONTO));
        }

        @Test
        void elPromedioSeCalculaSobreTodoElHistorial() {
            // (100 + 200 + 300) / 3 = 200 -> umbral 1000
            TransactionScore score = puntuar(
                    una().conId("actual").conMonto("1000.01").construir(),
                    una().conId("p1").conMonto("100.00").construir(),
                    una().conId("p2").conMonto("200.00").construir(),
                    una().conId("p3").conMonto("300.00").construir());

            assertTrue(activo(score, MONTO));
        }

        @Test
        void cualquierMontoPositivoSeActivaSiElPromedioHistoricoEsCero() {
            // Caso degenerado: con promedio 0 el umbral es 0, asi que una compra
            // de un centimo se marca como monto atipico.
            TransactionScore score = puntuar(
                    una().conId("actual").conMonto("0.01").construir(),
                    una().conId("p1").conMonto("0.00").construir());

            assertTrue(activo(score, MONTO), "promedio cero convierte cualquier importe en atipico");
        }

        @Test
        void montoCeroConPromedioCeroNoSeActiva() {
            TransactionScore score = puntuar(
                    una().conId("actual").conMonto("0.00").construir(),
                    una().conId("p1").conMonto("0.00").construir());

            assertFalse(activo(score, MONTO));
        }

        @Test
        void laReglaIgnoraLaMonedaYMezclaDivisasDistintas() {
            // Documenta un riesgo real: el promedio no distingue divisa, asi que
            // comparar 600 USD contra un historial en COP no significa nada.
            TransactionScore score = puntuar(
                    una().conId("actual").conMonto("600.00").conMoneda("USD").construir(),
                    una().conId("p1").conMonto("100.00").conMoneda("COP").construir());

            assertTrue(activo(score, MONTO),
                    "hoy se activa comparando importes de divisas distintas");
        }

        @Test
        void lanzaNullPointerSiUnMontoDelHistorialEsNulo() {
            // Alcanzable desde la cola: el poller deserializa sin validar.
            Transaction sinMonto = new Transaction(
                    "p1", "acc-1", null, "USD", T0, T0, 0.0, 0.0, "mer-1", "retail");

            assertThrows(NullPointerException.class,
                    () -> puntuar(una().conId("actual").construir(), sinMonto));
        }
    }

    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Regla GEO_IMPOSIBLE")
    class Geografia {

        @Test
        void noSeActivaConHistorialVacio() {
            TransactionScore score = puntuar(una().en(MADRID_LAT, MADRID_LON).construir());

            assertFalse(activo(score, GEO));
        }

        @Test
        void noSeActivaEnLaMismaCoordenadaExacta() {
            TransactionScore score = puntuar(
                    una().conId("actual").en(BOGOTA_LAT, BOGOTA_LON).enT0MasSegundos(1).construir(),
                    una().conId("p1").en(BOGOTA_LAT, BOGOTA_LON).en(T0).construir());

            assertFalse(activo(score, GEO));
        }

        @Test
        void saltoDeBogotaAMadridEnUnMinutoSeActiva() {
            TransactionScore score = puntuar(
                    una().conId("actual").en(MADRID_LAT, MADRID_LON).enT0MasSegundos(60).construir(),
                    una().conId("p1").en(BOGOTA_LAT, BOGOTA_LON).en(T0).construir());

            assertTrue(activo(score, GEO));
            assertEquals(17, score.score());
        }

        @Test
        void unVueloBogotaMadridEnNueveHorasNoSeActiva() {
            // 8011 km en 9 h = 890 km/h, plausible para un avion.
            TransactionScore score = puntuar(
                    una().conId("actual").en(MADRID_LAT, MADRID_LON).enT0MasSegundos(32400).construir(),
                    una().conId("p1").en(BOGOTA_LAT, BOGOTA_LON).en(T0).construir());

            assertFalse(activo(score, GEO), "890 km/h es una velocidad de vuelo razonable");
        }

        @Test
        void seUsaLaTransaccionMasRecienteDelHistorialNoLaPrimera() {
            // Si tomara la primera de la lista (Madrid) el salto seria enorme.
            TransactionScore score = puntuar(
                    una().conId("actual").en(BOGOTA_LAT, BOGOTA_LON).enT0MasSegundos(30).construir(),
                    una().conId("vieja").en(MADRID_LAT, MADRID_LON).enT0MasSegundos(-3600).construir(),
                    una().conId("reciente").en(BOGOTA_LAT, BOGOTA_LON).en(T0).construir());

            assertFalse(activo(score, GEO), "debe compararse contra la mas reciente, que esta en Bogota");
        }

        @Test
        void elCruceDelAntimeridianoSeCalculaPorElCaminoCorto() {
            // De 179.99 a -179.99 son 2.2 km, no media vuelta al mundo.
            TransactionScore conCruce = puntuar(
                    una().conId("actual").en(0.0, -179.99).enT0MasSegundos(3600).construir(),
                    una().conId("p1").en(0.0, 179.99).en(T0).construir());

            assertFalse(activo(conCruce, GEO),
                    "2.2 km en una hora no es un salto imposible; si fallara, el calculo daria la vuelta al globo");
        }

        @Test
        void elPoloNorteConLongitudesOpuestasEsElMismoPunto() {
            TransactionScore score = puntuar(
                    una().conId("actual").en(90.0, 180.0).enT0MasSegundos(1).construir(),
                    una().conId("p1").en(90.0, 0.0).en(T0).construir());

            assertFalse(activo(score, GEO), "en el polo todas las longitudes convergen");
        }

        @Test
        void unTiempoNegativoEntreTransaccionesSeTrataComoUnSegundo() {
            // La "ultima" del historial es posterior a la actual (reproceso
            // desordenado). El clamp convierte el intervalo negativo en 1 segundo,
            // lo que produce una velocidad enorme y un falso positivo.
            TransactionScore score = puntuar(
                    una().conId("actual").en(BOGOTA_LAT, BOGOTA_LON).en(T0).construir(),
                    una().conId("posterior").en(MADRID_LAT, MADRID_LON).enT0MasSegundos(300).construir());

            assertTrue(activo(score, GEO),
                    "hoy un intervalo negativo se trata como +1s y dispara la regla");
        }

        @Test
        void elSaltoAntipodalNoSeDetectaEnCiertasLatitudes() {
            // BUG REAL confirmado numericamente: en latitudes como 8, 12 u 82 el
            // termino 'a' del haversine supera 1 por error de coma flotante, la
            // raiz de (1-a) da NaN, la distancia es NaN y la comparacion
            // NaN > 1000 es false. Resultado: el mayor salto posible en la Tierra
            // (20015 km) NO activa la regla. En otras latitudes (0, 9, 45, 89) si
            // funciona, asi que el fallo es intermitente.
            TransactionScore score = puntuar(
                    una().conId("actual").en(-8.0, 180.0).enT0MasSegundos(3600).construir(),
                    una().conId("p1").en(8.0, 0.0).en(T0).construir());

            assertFalse(activo(score, GEO),
                    "documenta el fallo actual: el salto antipodal a 8 grados queda sin detectar. "
                            + "Al corregir el haversine (clamp de 'a' a 1.0 o usar asin) este test debe invertirse");
        }

        @Test
        void elMismoSaltoAntipodalSiSeDetectaEnElEcuador() {
            // Control del caso anterior: misma distancia, latitud distinta, si activa.
            TransactionScore score = puntuar(
                    una().conId("actual").en(0.0, 180.0).enT0MasSegundos(3600).construir(),
                    una().conId("p1").en(0.0, 0.0).en(T0).construir());

            assertTrue(activo(score, GEO), "el mismo salto en el ecuador si se detecta");
        }

        @Test
        void lanzaNullPointerSiUnaCoordenadaEsNula() {
            Transaction sinCoordenada = new Transaction(
                    "p1", "acc-1", new java.math.BigDecimal("100.00"), "USD",
                    T0, T0, null, 0.0, "mer-1", "retail");

            assertThrows(NullPointerException.class,
                    () -> puntuar(una().conId("actual").enT0MasSegundos(60).construir(), sinCoordenada));
        }
    }

    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Regla COMERCIO_RIESGO")
    class Comercio {

        @Test
        void seActivaConCadaCategoriaDeRiesgoConfigurada() {
            for (String categoria : List.of("gambling", "crypto", "adult")) {
                TransactionScore score = puntuar(una().conCategoria(categoria).construir());
                assertTrue(activo(score, COMERCIO), "deberia activarse con " + categoria);
                assertEquals(20, score.score());
            }
        }

        @Test
        void laComparacionNoDistingueMayusculas() {
            for (String categoria : List.of("GAMBLING", "Gambling", "GaMbLiNg")) {
                TransactionScore score = puntuar(una().conCategoria(categoria).construir());
                assertTrue(activo(score, COMERCIO), "deberia activarse con " + categoria);
            }
        }

        @Test
        void noSeActivaConUnaCategoriaSegura() {
            TransactionScore score = puntuar(una().conCategoria("retail").construir());

            assertFalse(activo(score, COMERCIO));
            assertEquals(0, score.score());
        }

        @Test
        void losEspaciosAlrededorImpidenLaCoincidencia() {
            // No hay trim(): un espacio de mas evade la regla en silencio.
            TransactionScore score = puntuar(una().conCategoria(" gambling").construir());

            assertFalse(activo(score, COMERCIO),
                    "hoy un espacio sobrante evade la deteccion; si se anade trim() este test cambia");
        }

        @Test
        void unaSubcadenaNoCuentaComoCoincidencia() {
            TransactionScore score = puntuar(una().conCategoria("online-gambling").construir());

            assertFalse(activo(score, COMERCIO), "la comparacion es por igualdad exacta, no por contenido");
        }

        @Test
        void esLaUnicaReglaQueSeActivaSinHistorial() {
            TransactionScore score = puntuar(una().conCategoria("gambling").construir());

            assertEquals(20, score.score());
            assertEquals(1, score.activations().size());
        }

        @Test
        void noSeActivaSiLaListaDeCategoriasEstaVacia() {
            propiedades.setRiskMerchantCategories(List.of());

            TransactionScore score = puntuar(una().conCategoria("gambling").construir());

            assertFalse(activo(score, COMERCIO));
        }
    }

    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Combinaciones de reglas y umbral de fraude")
    class Combinaciones {

        /** Historial y transaccion que activan exactamente las reglas pedidas. */
        private TransactionScore combinar(boolean velocidad, boolean monto, boolean geo, boolean comercio) {
            Instant actual = T0.plusSeconds(180);
            String categoria = comercio ? "gambling" : "retail";
            String importe = monto ? "600.00" : "100.00";
            double lat = geo ? MADRID_LAT : BOGOTA_LAT;
            double lon = geo ? MADRID_LON : BOGOTA_LON;

            Transaction tx = una().conId("actual").conCuenta("acc-comb")
                    .conMonto(importe).en(lat, lon).conCategoria(categoria).en(actual).construir();

            // Una previa basta para monto y geo; dos para alcanzar el minimo de velocidad.
            Transaction p1 = una().conId("p1").conCuenta("acc-comb")
                    .conMonto("100.00").en(BOGOTA_LAT, BOGOTA_LON).enT0MasSegundos(120).construir();
            if (!velocidad) {
                return puntuar(tx, p1);
            }
            Transaction p2 = una().conId("p2").conCuenta("acc-comb")
                    .conMonto("100.00").en(BOGOTA_LAT, BOGOTA_LON).enT0MasSegundos(60).construir();
            return puntuar(tx, p1, p2);
        }

        @Test
        void sinNingunaReglaElScoreEsCero() {
            TransactionScore score = combinar(false, false, false, false);

            assertEquals(0, score.score());
            assertTrue(score.activations().isEmpty());
        }

        @Test
        void cadaReglaPorSeparadoAportaSusPuntos() {
            assertEquals(35, combinar(true, false, false, false).score());
            assertEquals(30, combinar(false, true, false, false).score());
            assertEquals(17, combinar(false, false, true, false).score());
            assertEquals(20, combinar(false, false, false, true).score());
        }

        @Test
        void velocidadMasComercioEsElMaximoQueNoAbreCaso() {
            // 35 + 20 = 55, por debajo del umbral de 60.
            TransactionScore score = combinar(true, false, false, true);

            assertEquals(55, score.score());
            assertFalse(score.score() > score.threshold(), "55 no debe superar el umbral de 60");
        }

        @Test
        void velocidadMasMontoEsElMinimoQueAbreCaso() {
            // 35 + 30 = 65, el salto inmediato por encima de 55.
            TransactionScore score = combinar(true, true, false, false);

            assertEquals(65, score.score());
            assertTrue(score.score() > score.threshold());
        }

        @Test
        void lasCuatroReglasJuntasDanElScoreMaximo() {
            TransactionScore score = combinar(true, true, true, true);

            assertEquals(102, score.score());
            assertEquals(4, score.activations().size());
        }

        @Test
        void ningunaCombinacionDeReglasSumaExactamenteElUmbral() {
            // Los 16 scores posibles saltan de 55 a 65: no hay forma de obtener 60
            // con la configuracion de produccion. Por eso la frontera exacta del
            // umbral solo puede probarse cambiando el umbral, no las reglas.
            for (int mascara = 0; mascara < 16; mascara++) {
                TransactionScore score = combinar(
                        (mascara & 1) != 0, (mascara & 2) != 0,
                        (mascara & 4) != 0, (mascara & 8) != 0);
                assertFalse(score.score() == 60,
                        "ninguna combinacion deberia sumar exactamente 60, pero la mascara "
                                + mascara + " dio " + score.score());
            }
        }

        @Test
        void elUmbralExactoNoAbreCasoPorqueLaComparacionEsEstricta() {
            propiedades.setThreshold(55);

            TransactionScore score = combinar(true, false, false, true);

            assertEquals(55, score.score());
            assertFalse(score.score() > score.threshold(), "score igual al umbral no debe abrir caso");
        }

        @Test
        void unPuntoPorEncimaDelUmbralSiAbreCaso() {
            propiedades.setThreshold(54);

            TransactionScore score = combinar(true, false, false, true);

            assertTrue(score.score() > score.threshold());
        }
    }

    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Contrato del resultado")
    class Contrato {

        @Test
        void elScoreEsLaSumaDeLosPuntosDeLasActivaciones() {
            TransactionScore score = puntuar(
                    una().conId("actual").conMonto("600.00").conCategoria("gambling")
                            .en(MADRID_LAT, MADRID_LON).enT0MasSegundos(60).construir(),
                    una().conId("p1").conMonto("100.00").en(BOGOTA_LAT, BOGOTA_LON).en(T0).construir());

            int suma = score.activations().stream().mapToInt(RuleActivation::points).sum();
            assertEquals(suma, score.score());
        }

        @Test
        void lasActivacionesSiempreLleganEnElMismoOrden() {
            TransactionScore score = puntuar(
                    una().conId("actual").conCuenta("acc-x").conMonto("600.00").conCategoria("gambling")
                            .en(MADRID_LAT, MADRID_LON).enT0MasSegundos(180).construir(),
                    una().conId("p1").conCuenta("acc-x").conMonto("100.00")
                            .en(BOGOTA_LAT, BOGOTA_LON).enT0MasSegundos(60).construir(),
                    una().conId("p2").conCuenta("acc-x").conMonto("100.00")
                            .en(BOGOTA_LAT, BOGOTA_LON).enT0MasSegundos(120).construir());

            List<String> reglas = score.activations().stream().map(RuleActivation::ruleId).toList();
            assertEquals(List.of(VELOCIDAD, MONTO, GEO, COMERCIO), reglas);
        }

        @Test
        void elUmbralSeCongelaEnElMomentoDelCalculo() {
            TransactionScore score = puntuar(una().construir());

            assertEquals(60, score.threshold());
        }

        @Test
        void laListaDeActivacionesEsInmutable() {
            TransactionScore score = puntuar(una().conCategoria("gambling").construir());

            assertThrows(UnsupportedOperationException.class,
                    () -> score.activations().add(new RuleActivation("X", 1, List.of())));
        }

        @Test
        void sinActivacionesLaListaLlegaVaciaYNoNula() {
            TransactionScore score = puntuar(una().construir());

            assertTrue(score.activations().isEmpty());
            assertEquals(0, score.score());
        }

        @Test
        void conservaElIdentificadorDeLaTransaccionPuntuada() {
            TransactionScore score = puntuar(una().conId("tx-abc").construir());

            assertEquals("tx-abc", score.transactionId());
        }
    }
}
