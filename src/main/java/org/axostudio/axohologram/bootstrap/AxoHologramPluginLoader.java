package org.axostudio.axohologram.bootstrap;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Lets Paper resolve runtime libraries into its cache before Kotlin code is
 * loaded. This keeps the plugin JAR compact while retaining SQLite-backed
 * import support.
 */
public final class AxoHologramPluginLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder(
                "central",
                "default",
                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
        ).build());

        // Match the Kotlin compiler version exactly. Kotlin reflection is not
        // loaded because AxoHologram does not use it.
        addLibrary(resolver, "org.jetbrains.kotlin:kotlin-stdlib:2.3.0");
        addLibrary(resolver, "org.xerial:sqlite-jdbc:3.50.3.0");
        classpathBuilder.addLibrary(resolver);
    }

    private void addLibrary(MavenLibraryResolver resolver, String coordinates) {
        resolver.addDependency(new Dependency(new DefaultArtifact(coordinates), null));
    }
}
