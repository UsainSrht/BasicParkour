package me.usainsrht.basicparkour.core;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

/**
 * Paper plugin loader — runs before the plugin class is instantiated.
 *
 * <p>Adds runtime libraries to the plugin classpath via Paper's Maven resolver
 * so they do not need to be shaded into the JAR. The resolved artifacts are
 * downloaded on first start and cached by the server.</p>
 *
 * <h2>Libraries resolved here</h2>
 * <ul>
 *   <li>HikariCP — JDBC connection pool</li>
 *   <li>SQLite JDBC — embedded SQLite driver</li>
 *   <li>MySQL Connector/J — MySQL / MariaDB driver</li>
 * </ul>
 *
 * <p>bStats is intentionally <em>not</em> resolved here; it is shaded into the
 * JAR (with relocation) to avoid version conflicts with other plugins.</p>
 */
@SuppressWarnings("UnstableApiUsage")
public final class BasicParkourLoader implements PluginLoader {

    // ── Dependency coordinates — keep in sync with root pom.xml properties ───
    private static final String HIKARI_COORDS   = "com.zaxxer:HikariCP:5.1.0";
    private static final String SQLITE_COORDS   = "org.xerial:sqlite-jdbc:3.45.3.0";
    private static final String MYSQL_COORDS    = "com.mysql:mysql-connector-j:8.4.0";

    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        // Use Paper's default central mirror (avoids Maven Central ToS / rate limits)
        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default",
                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
        ).build());

        // Paper's own repo (needed if any dep is only on PaperMC repo)
        resolver.addRepository(new RemoteRepository.Builder(
                "papermc", "default",
                "https://repo.papermc.io/repository/maven-public/"
        ).build());

        resolver.addDependency(new Dependency(new DefaultArtifact(HIKARI_COORDS), null));
        resolver.addDependency(new Dependency(new DefaultArtifact(SQLITE_COORDS), null));
        resolver.addDependency(new Dependency(new DefaultArtifact(MYSQL_COORDS), null));

        classpathBuilder.addLibrary(resolver);
    }
}
