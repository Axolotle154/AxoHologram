[![](https://jitpack.io/v/Axolotle154/AxoHologram.svg)](https://jitpack.io/#Axolotle154/AxoHologram)
# AxoHologram

Sistema de hologramas para **Paper** basado en **Display Entities**.

AxoHologram esta orientado a hologramas de texto, item y bloque, con edicion por comandos, paginas, formato avanzado de texto, animaciones configurables e integraciones opcionales.

## Requisitos

- **Paper** `1.21.4+`
- **Java** `25+`

## Caracteristicas

- Hologramas basados en `TextDisplay`, `ItemDisplay` y `BlockDisplay`
- Tipos de linea: `TEXT`, `ITEM`, `BLOCK`
- Texto con MiniMessage, HEX y formato legacy `&` / `\u00A7`
- PlaceholderAPI opcional
- MiniPlaceholders opcional
- Paginas multiples por holograma
- Visibilidad por `all`, `manual` o `permission`
- Estilo configurable: `billboard`, `scale`, `shadow`, `background`, `brightness`, `alignment`
- Posicionamiento por coordenadas, rotacion, offset base y offset por linea
- Animaciones de texto y display desde un unico `animations.yml`
- Integracion opcional con AxoNPCs y FancyNPCs
- API publica para otros plugins
- Archivos separados por holograma en `plugins/AxoHologram/holograms/<id>.yml`

## Instalacion

1. Compila el proyecto:

```bash
mvn clean package
```

2. Copia el jar generado en `plugins/`.
3. Inicia el servidor.

El jar se genera en:

```text
target/AxoHologram-1.1.4-alpha.jar
```

## Comando base

Comando principal:

```text
/holograma
```

Comandos equivalentes:

```text
/axohologram
/aholo
/axoholo
/holo
```

## Comandos

### Gestion basica

| Comando | Descripcion |
|---|---|
| `/holograma create <text\|item\|block> <id>` | Crea un holograma nuevo |
| `/holograma create <id>` | Compatibilidad legacy, crea un holograma `text` |
| `/holograma clone <source_id> <new_id>` | Clona un holograma existente |
| `/holograma delete <id>` | Elimina un holograma |
| `/holograma list` | Lista hologramas cargados |
| `/holograma reload` | Recarga configuracion, hologramas y animaciones |
| `/holograma version` | Muestra la version actual del plugin |
| `/holograma ver` | Alias de `version` |
| `/holograma teleport <id>` | Teleporta al jugador al holograma |

### Posicion y movimiento

| Comando | Descripcion |
|---|---|
| `/holograma movehere <id>` | Mueve el holograma a tu posicion |
| `/holograma move <id>` | Alias legacy de `movehere` |
| `/holograma moveto <id> <x> <y> <z> [yaw] [pitch]` | Mueve el holograma a coordenadas |
| `/holograma rotate <id> <degrees>` | Cambia la rotacion horizontal |
| `/holograma rotatepitch <id> <degrees>` | Cambia la rotacion vertical |
| `/holograma offset <id> <x> <y> <z>` | Cambia el offset base del holograma |

Si un holograma esta vinculado a un NPC, no puede moverse manualmente hasta hacer `unlink`.

### Paginas

| Comando | Descripcion |
|---|---|
| `/holograma page add <id>` | Anade una pagina |
| `/holograma page delete <id> <page>` | Elimina una pagina |
| `/holograma page remove <id> <page>` | Alias legacy de `page delete` |
| `/holograma page default <id> <page>` | Define la pagina por defecto |
| `/holograma page set <id> <page>` | Alias legacy de `page default` |

### Lineas

| Comando | Descripcion |
|---|---|
| `/holograma line add <id> <page> <type> <content>` | Anade una linea |
| `/holograma line set <id> <page> <line> <content>` | Cambia el contenido de una linea |
| `/holograma line delete <id> <page> <line>` | Elimina una linea |
| `/holograma line remove <id> <page> <line>` | Alias legacy de `line delete` |
| `/holograma line offset <id> <page> <line> <x> <y> <z>` | Cambia el offset de una linea |
| `/holograma line scale <id> <page> <line> <factor\|x y z\|default>` | Cambia la escala de una linea `item` o `block` |

Tipos de linea validos:

```text
text
item
block
```

Ejemplos:

```text
/holograma line add bienvenida 1 text <gradient:#9D00FF:#00E5FF>Hola</gradient>
/holograma line add espada 1 item DIAMOND_SWORD
/holograma line add bloque 1 block GRASS_BLOCK
```

### Permisos de visualizacion

| Comando | Descripcion |
|---|---|
| `/holograma permission <id> <permission>` | Define un permiso personalizado |
| `/holograma permission <id>` | Limpia el permiso personalizado y vuelve a `all` |

Permiso efectivo por defecto si usas visibilidad por permiso:

```text
axohologram.view.<id>
```

### Visibilidad y estilo

| Comando | Descripcion |
|---|---|
| `/holograma viewdistance <id> <distance>` | Cambia la distancia de render |
| `/holograma viewdistance <id> default` | Vuelve a la distancia por defecto del config |
| `/holograma visibility <id> <all\|manual\|permission>` | Cambia el modo de visibilidad |
| `/holograma scale <id> <factor>` | Cambia la escala |
| `/holograma billboard <id> <center\|fixed\|vertical\|horizontal>` | Cambia el billboard |
| `/holograma shadow strength <id> <value>` | Cambia la fuerza de sombra |
| `/holograma shadow radius <id> <value>` | Cambia el radio de sombra |
| `/holograma background <id> <color\|transparent\|transparente>` | Cambia el fondo del texto |
| `/holograma textshadow <id> <true\|false>` | Activa o desactiva sombra de texto |
| `/holograma brightness <id> <block\|sky> <0-15>` | Cambia el brillo |
| `/holograma align <id> <center\|left\|right>` | Cambia la alineacion del texto |

### NPCs

| Comando | Descripcion |
|---|---|
| `/holograma npc link <holograma> <npc>` | Vincula un holograma a un NPC |
| `/holograma npc unlink <holograma>` | Quita el vinculo |
| `/holograma npc info <holograma>` | Muestra el estado del vinculo |

## Permisos

| Permiso | Descripcion |
|---|---|
| `axohologram.admin` | Acceso administrativo total |
| `axohologram.create` | Crear hologramas |
| `axohologram.delete` | Eliminar hologramas |
| `axohologram.edit` | Permiso legacy de edicion general |
| `axohologram.reload` | Recargar el plugin |
| `axohologram.list` | Listar hologramas |
| `axohologram.teleport` | Teleport a hologramas |
| `axohologram.hologram.move` | Mover, rotar y cambiar offset base |
| `axohologram.hologram.visibility` | Editar visibilidad y view distance |
| `axohologram.hologram.style` | Editar estilo y apariencia |
| `axohologram.page.edit` | Editar paginas |
| `axohologram.line.edit` | Editar lineas |
| `axohologram.permission.edit` | Editar permisos de holograma |
| `axohologram.npc.edit` | Vincular y desvincular NPCs |
| `axohologram.npc.info` | Ver informacion del vinculo con NPCs |
| `axohologram.view.<id>` | Ver un holograma concreto si usa visibilidad por permiso |

## Formato de texto

Soporta:

- MiniMessage
- Legacy `&`
- Legacy `\u00A7`
- HEX `&#RRGGBB`
- HEX estilo Bungee `&x&F&F&0&0&F&F`
- Animaciones con `<#ANIM:nombre>texto</#ANIM>`
- Animaciones con `<anim:nombre>texto</anim:nombre>`
- Animaciones inline con `<#ANIM:&f:&b&l>texto</#ANIM>`

Ejemplos:

```text
<red>Hola
&aHola
&#9D00FF&lTexto
&x&9&D&0&0&F&FTexto
<#ANIM:rainbow>Hello</#ANIM>
<#ANIM:pulse_blue>Store</#ANIM>
<anim:wave_aqua>Coins</anim:wave_aqua>
<#ANIM:&f:&b&l>Hello World</#ANIM>
```

## Animaciones

Todas las animaciones se cargan desde un unico archivo:

```text
plugins/AxoHologram/animations.yml
```

No se usa carpeta `animations/`. El archivo contiene:

- configuracion global
- animaciones de texto
- animaciones display
- animaciones custom
- presets
- asignaciones por holograma

Ejemplo de asignacion por holograma:

```yaml
holograms:
  spawn_info:
    display-animation: cinematic_idle
```

Los hologramas tambien pueden activar o desactivar su animacion display:

```yaml
display-animation-enabled: false
display-animation: cinematic_idle
```

Para activar esa animacion en un holograma concreto, cambia `display-animation-enabled` a `true`. Para hologramas nuevos, el default vive en `config.yml`:

```yaml
general:
  defaults:
    display-animation:
      enabled: false
      name: cinematic_idle
```

Ejemplo de animacion de texto:

```text
<#ANIM:wave_aqua>Coins</#ANIM>
<anim:pulse_blue>Store</anim:pulse_blue>
```

El plugin tambien crea `plugins/AxoHologram/holograms/animation_example.yml` si no existe. Ese holograma queda con `enabled: false` por defecto para usarlo como plantilla; cambia a `enabled: true` y recarga el plugin para verlo.

## Integraciones opcionales

### PlaceholderAPI

Si esta instalado y habilitado en config, se parsean placeholders por jugador en texto.

### AxoNPCs y FancyNPCs

Si esta instalado y habilitado en config:

- se pueden vincular hologramas a NPCs
- el holograma sigue la posicion del NPC

### MiniPlaceholders

Si esta instalado y habilitado en config:

- se parsean placeholders basados en MiniMessage
- se soportan placeholders globales y por audiencia
- los hologramas con placeholders detectados entran en el ciclo de refresh periodico

## API publica

Otros plugins pueden usar la API con `AxoHologram.getAPI()` o mediante `ServicesManager`.

La API ya permite crear hologramas de texto, item, bloque y tambien hologramas mixtos usando lineas raw.

Ejemplo rapido con lineas mixtas:

```java
AxoHologramAPI api = AxoHologram.getAPI();

api.createHologram(
        "afk_zone",
        location,
        List.of(
                api.createTextLine("&8&m--------&r &c&l✪ &4&lZONA AFK &c&l✪ &r&8&m--------"),
                api.createTextLine(""),
                api.createItemLine("PAPER"),
                api.createItemLine("PLAYER_HEAD(%player_name%)"),
                api.createBlockLine("END_PORTAL_FRAME")
        )
);
```

Ejemplo para heads dinamicas desde API:

```java
AxoHologramAPI api = AxoHologram.getAPI();

api.createItemHologram(
        "player_head",
        location,
        "#ITEM:PLAYER_HEAD(%player_name%)"
);

api.addItemLine(
        "player_head",
        "#ITEM:PLAYER_HEAD(%player_uuid%)"
);
```

`PLAYER_HEAD(...)` acepta nombre, UUID, `base64:`/`value:`, URL de textura y hash de textura.

Dependencia Maven recomendada si publicas AxoHologram en JitPack:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.AxoStudio</groupId>
        <artifactId>AxoHologram</artifactId>
        <version>1.2.3-alpha</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

## Archivos de configuracion

Se generan:

```text
plugins/AxoHologram/config.yml
plugins/AxoHologram/messages.yml
plugins/AxoHologram/animations.yml
plugins/AxoHologram/holograms/
```

Cada holograma persistente se guarda en su propio archivo:

```text
plugins/AxoHologram/holograms/spawn.yml
plugins/AxoHologram/holograms/shop.yml
plugins/AxoHologram/holograms/rules.yml
```

## Notas

- El plugin esta orientado a **Paper**, no a Bukkit/Spigot como target principal.
- Los hologramas simples de texto pueden renderizarse como un solo `TextDisplay`.
- Los hologramas vinculados a NPCs priorizan la posicion del NPC sobre movimiento manual.
- Los hologramas temporales creados por API no se guardan en YAML.

## Tipos de animaciones

En la practica, el plugin trabaja con cuatro grupos de animaciones:

- **Animaciones de texto**: cambian colores o el estilo del texto mientras se renderiza. En el ejemplo del proyecto vienen `rainbow`, `pulse`, `matrix` y `wave`.
- **Animaciones de display**: mueven o transforman visualmente el holograma. En el archivo de ejemplo aparecen `float`, `spin`, `cinematic-idle` y `orbit`.
- **Animaciones custom por frames**: te dejan definir cada frame a mano con `frame-animation`, util si quieres un ciclo muy concreto y no solo un efecto generico.
- **Presets**: combinan una animacion de texto y una de display en una sola configuracion para reutilizarla rapido.

Si quieres verlo rapido en `animations.yml`, un ejemplo real seria este:

```yaml
text:
  wave_aqua:
    type: wave

display:
  cinematic_idle:
    type: cinematic-idle

custom:
  rainbow_cycle:
    type: frame-animation

presets:
  store-title:
    text-animation: pulse_blue
    display-animation: cinematic_idle
```

## Ejemplos de comandos

Algunos ejemplos reales para el dia a dia:

```text
/holograma create text bienvenida
/holograma line set bienvenida 1 1 <gradient:#9D00FF:#00E5FF>Bienvenido al servidor</gradient>
/holograma page add bienvenida
/holograma line add bienvenida 2 text <yellow>/warp minas
/holograma movehere bienvenida
/holograma visibility bienvenida permission
/holograma permission bienvenida axohologram.view.bienvenida
/holograma create item tienda
/holograma line set tienda 1 1 DIAMOND_SWORD
/holograma line scale tienda 1 1 2
/holograma create block portal
/holograma line set portal 1 1 END_PORTAL_FRAME
/holograma line scale portal 1 1 1.5 1.5 1.5
/holograma npc link bienvenida Comerciante
/holograma reload
```

En corto: `create` para crearlo, `line add` o `line set` para editarlo, `movehere` para colocarlo donde estas parado y `reload` cuando cambias archivos a mano y quieres recargar todo sin reiniciar el servidor.

Tambien puedes usar el alias corto `addline`, que en la practica hace lo mismo que `line add`. Por ejemplo, si quieres meter una linea de texto con animacion inline en la pagina 1:

```text
/holo addline <ID> 1 text <#ANIM:&f:&b&l>Hello World</#ANIM>
```

Ese comando se interpreta asi:

- `<ID>` es el id del holograma.
- `1` es la pagina donde se agrega la linea.
- `text` indica que la linea sera de texto.
- `<#ANIM:&f:&b&l>Hello World</#ANIM>` aplica una animacion inline directamente sobre ese texto.

Si prefieres la forma larga, seria equivalente a esto:

```text
/holograma line add <ID> 1 text <#ANIM:&f:&b&l>Hello World</#ANIM>
```
