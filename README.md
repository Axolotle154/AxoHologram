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
- Integracion opcional con FancyNPCs
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

Si un holograma esta vinculado a FancyNPCs, no puede moverse manualmente hasta hacer `unlink`.

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

### FancyNPCs

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
| `axohologram.npc.edit` | Vincular y desvincular FancyNPCs |
| `axohologram.npc.info` | Ver informacion del vinculo con FancyNPCs |
| `axohologram.view.<id>` | Ver un holograma concreto si usa visibilidad por permiso |

## Formato de texto

Soporta:

- MiniMessage
- Legacy `&`
- Legacy `\u00A7`
- HEX `&#RRGGBB`
- HEX estilo Bungee `&x&F&F&0&0&F&F`
- Animaciones con `<#ANIM:nombre>texto</#ANIM>`

Ejemplos:

```text
<red>Hola
&aHola
&#9D00FF&lTexto
&x&9&D&0&0&F&FTexto
<#ANIM:rainbow>Hello</#ANIM>
<#ANIM:pulse_blue>Store</#ANIM>
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

Ejemplo de animacion de texto:

```text
<#ANIM:wave_aqua>Coins</#ANIM>
```

## Integraciones opcionales

### PlaceholderAPI

Si esta instalado y habilitado en config, se parsean placeholders por jugador en texto.

### FancyNPCs

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
        <version>1.1.4-alpha</version>
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
- Los hologramas vinculados a FancyNPCs priorizan la posicion del NPC sobre movimiento manual.
- Los hologramas temporales creados por API no se guardan en YAML.
