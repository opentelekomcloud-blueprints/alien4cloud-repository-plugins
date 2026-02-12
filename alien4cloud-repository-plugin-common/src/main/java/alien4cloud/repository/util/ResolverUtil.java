package alien4cloud.repository.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import alien4cloud.component.repository.IArtifactResolver;
import alien4cloud.component.repository.IConfigurableArtifactResolver;
import alien4cloud.deployment.exceptions.UnresolvableArtifactException;
import alien4cloud.repository.configuration.SimpleConfiguration;
import alien4cloud.repository.exception.ResolverNotConfiguredException;
import org.alien4cloud.tosca.normative.constants.NormativeCredentialConstant;
import alien4cloud.utils.FileUtil;

public class ResolverUtil {

    public static boolean isResolverTypeCompatible(IArtifactResolver resolver, String inputType) {
        // If a type is defined then it must match the one of the resolver
        return StringUtils.isBlank(inputType) || inputType.equals(resolver.getResolverType());
    }

    public static boolean isCredentialsBasicUserPassword(Map<String, Object> credentials) {
        // It must be something like user:password with user not null
        return MapUtils.isEmpty(credentials) || (credentials.containsKey("user") && credentials.containsKey("token"));
    }

    public static <T> T getMandatoryConfiguration(IConfigurableArtifactResolver<T> resolver) {
        T conf = resolver.getConfiguration();
        if (conf == null) {
            throw new ResolverNotConfiguredException("Resolver is not configured yet");
        }
        return conf;
    }

    private static Path createTemporaryArtifactFile(Path tempDir, String artifactRef) throws IOException {
        return Files.createTempFile(tempDir, "", "." + FilenameUtils.getExtension(artifactRef));
    }

    public static Path copyArtifactStreamToTempFile(String artifactRef, InputStream artifactStream, Path tempDir) {
        try {
            Path artifactPath = createTemporaryArtifactFile(tempDir, artifactRef);
            Files.copy(artifactStream, artifactPath, StandardCopyOption.REPLACE_EXISTING);
            return artifactPath;
        } catch (IOException e) {
            throw new UnresolvableArtifactException("Could not copy artifact " + artifactRef, e);
        }
    }

    public static Path copyArtifactToTempFile(String artifactRef, Path artifact, Path tempDir) throws IOException {
        if (Files.isRegularFile(artifact)) {
            Path output = createTemporaryArtifactFile(tempDir, artifactRef);
            Files.copy(artifact, output, StandardCopyOption.REPLACE_EXISTING);
            return output;
        } else {
            Path output = Files.createTempDirectory(tempDir, "");
            FileUtil.copy(artifact, output);
            return output;
        }
    }

    public static Path createPluginTemporaryDownloadDir(String baseDir, String tempDirName) throws IOException {
        Path uploadDir = StringUtils.isNotBlank(tempDirName) ? Paths.get(baseDir).resolve(tempDirName) : Paths.get(baseDir);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
        return uploadDir;
    }

    public static Map<String, Object> getConfiguredCredentials(IConfigurableArtifactResolver<? extends SimpleConfiguration> resolver,
            Map<String, Object> defaultValue) {
        SimpleConfiguration preConfiguration = ResolverUtil.getMandatoryConfiguration(resolver);
        if (preConfiguration.getUser() != null) {
            return ImmutableMap.<String, Object> builder().put(NormativeCredentialConstant.USER_KEY, preConfiguration.getUser())
                    .put(NormativeCredentialConstant.TOKEN_KEY, preConfiguration.getPassword()).build();
        } else {
            return defaultValue;
        }
    }
}
