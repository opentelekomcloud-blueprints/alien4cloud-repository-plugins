package alien4cloud.repository.http;

import static alien4cloud.repository.http.HttpUtil.isHttpURL;

import java.util.Map;

import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;

import alien4cloud.component.repository.IConfigurableArtifactResolver;
import alien4cloud.component.repository.exception.InvalidResolverConfigurationException;
import alien4cloud.repository.model.ValidationResult;
import alien4cloud.repository.model.ValidationStatus;
import alien4cloud.repository.util.ResolverUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigurableHttpArtifactResolver implements IConfigurableArtifactResolver<HttpArtifactResolverConfiguration> {

    @Resource
    private HttpArtifactResolver httpArtifactResolver;

    private HttpArtifactResolverConfiguration configuration;

    public HttpArtifactResolverConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public String getConfigurationUrl() {
        return configuration.getUrl();
    }

    @Override
    public void setConfiguration(HttpArtifactResolverConfiguration configuration) {
        if (!isHttpURL(configuration.getUrl())) {
            throw new InvalidResolverConfigurationException("Resolver's configuration is incorrect, URL must be defined and begins with 'http' or 'https'");
        }
        this.configuration = configuration;
    }

    @Override
    public ValidationResult canHandleArtifact(String artifactReference, String repositoryURL, String repositoryType, Map<String, Object> credentials) {
        if (StringUtils.isNotBlank(repositoryURL) && !repositoryURL.equals(ResolverUtil.getMandatoryConfiguration(this).getUrl())) {
            return new ValidationResult(ValidationStatus.INVALID_REPOSITORY_URL, "Artifact's repository's URL does not match configuration");
        } else {
            return httpArtifactResolver.canHandleArtifact(artifactReference, ResolverUtil.getMandatoryConfiguration(this).getUrl(), repositoryType,
                    ResolverUtil.getConfiguredCredentials(this, credentials));
        }
    }

    private ValidationResult validateArtifact(String artifactReference, String repositoryURL, String repositoryType, Map<String, Object> credentials) {
        if (StringUtils.isNotBlank(repositoryURL) && !repositoryURL.equals(ResolverUtil.getMandatoryConfiguration(this).getUrl())) {
            return new ValidationResult(ValidationStatus.INVALID_REPOSITORY_URL, "Artifact's repository's URL does not match configuration");
        } else {
            return httpArtifactResolver.validateArtifact(artifactReference, ResolverUtil.getMandatoryConfiguration(this).getUrl(), repositoryType, credentials);
        }
    }

    @Override
    public String resolveArtifact(String artifactReference, String repositoryURL, String repositoryType, Map<String, Object> credentials) {
        if (!validateArtifact(artifactReference, repositoryURL, repositoryType, credentials).equals(ValidationResult.SUCCESS)) {
            return null;
        }
        return httpArtifactResolver.doResolveArtifact(artifactReference, ResolverUtil.getMandatoryConfiguration(this).getUrl(),
                ResolverUtil.getConfiguredCredentials(this, credentials));
    }
}
