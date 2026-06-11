package org.axostudio.axohologram;

import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

public final class AxoHologramPluginLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addDependency(new Dependency(new DefaultArtifact("org.xerial:sqlite-jdbc:3.46.1.3"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.jcodec:jcodec:0.2.5"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.jcodec:jcodec-javase:0.2.5"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("com.twelvemonkeys.imageio:imageio-webp:3.12.0"), null));
        resolver.addRepository(new RemoteRepository.Builder(
                "central",
                "default",
                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
        ).build());
        classpathBuilder.addLibrary(resolver);
    }
}
