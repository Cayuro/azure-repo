# Justificación de la región de despliegue: East US

## Contexto

Todos los recursos del proyecto (grupo de recursos `rg-centinela-prod`, incluyendo cómputo, almacenamiento y demás servicios asociados a Centinela) están desplegados en Azure bajo la región **East US**. Este documento resume las razones detrás de esa decisión.

## Región seleccionada: East US

**East US** (Virginia) es una de las regiones más antiguas, grandes y completas de Azure a nivel global. Para un proyecto operado desde Colombia, es la opción más razonable frente a alternativas aparentemente "más cercanas" como **Brazil South** (São Paulo).

## Razones principales

### 1. Conectividad y latencia real hacia Colombia

Aunque Brazil South está geográficamente más cerca de Sudamérica, la mayoría del tráfico de internet de Colombia hacia el resto del mundo no se enruta directamente hacia Brasil, sino a través de **Miami**, que funciona como el principal punto de interconexión (hub) de las Américas (NAP of the Americas). Colombia cuenta con múltiples cables submarinos de alta capacidad con destino a Miami (ARCOS, Maya-1, PAN-AM, entre otros).

Dado que East US tiene una interconexión troncal excelente con Miami, la latencia percibida desde Colombia hacia East US termina siendo comparable o incluso mejor que hacia Brazil South, ya que en muchos casos el tráfico hacia Brasil también pasa primero por Miami antes de continuar hacia São Paulo, sumando saltos adicionales en la ruta. La cercanía geográfica en el mapa no siempre se traduce en menor latencia real de red.

> Nota: si se requiere una validación cuantitativa, se recomienda correr pruebas de `ping`/`traceroute`/`mtr` desde un ISP colombiano hacia endpoints públicos de East US y Brazil South para confirmar esta relación en la práctica actual.

### 2. Costo

Brazil South tiene precios sensiblemente más altos que la mayoría de regiones de Azure en Norteamérica, principalmente por impuestos y cargos locales que Microsoft traslada al cliente (impuestos de importación/ICMS aplicables a servicios en la nube en Brasil). East US, al ser una región core en EE. UU., mantiene los precios base de lista de Azure sin estos sobrecostos, lo cual es relevante para un proyecto con un presupuesto mensual controlado (ver `budgetCentinel.md`, presupuesto de USD 60/mes).

### 3. Madurez, disponibilidad de servicios y zonas de disponibilidad

East US es una de las regiones "flagship" de Azure:

- Es de las primeras regiones en recibir nuevos servicios y features en preview/GA.
- Tiene el catálogo más completo de SKUs de cómputo, bases de datos y servicios de IA/ML.
- Cuenta con **3 zonas de disponibilidad (Availability Zones)**, lo que permite mayor resiliencia ante fallas de datacenter.
- Brazil South, en contraste, es una región más pequeña, con menor disponibilidad de algunos servicios y SKUs, y sin el mismo nivel de soporte para todas las features nuevas desde el día uno.

### 4. Ecosistema, documentación y soporte

La gran mayoría de tutoriales, ejemplos oficiales de Microsoft, plantillas de ARM/Bicep y foros de la comunidad usan East US como región de referencia por defecto. Esto reduce fricción al momento de depurar problemas, seguir documentación oficial o replicar configuraciones recomendadas.

### 5. Consistencia operativa

Al tener **todos los servicios del proyecto en una sola región (East US)**, se evita:

- Latencia adicional por comunicación cross-region entre servicios.
- Costos de egress de datos entre regiones.
- Complejidad de gestión de identidades, redes virtuales y políticas replicadas en múltiples regiones.

## Conclusión

East US ofrece, para un proyecto operado desde Colombia, una combinación superior de **latencia real, costo, madurez de servicios y resiliencia** frente a alternativas geográficamente más cercanas en el mapa como Brazil South. Por eso todos los servicios de Centinela se mantienen concentrados en esta región.
