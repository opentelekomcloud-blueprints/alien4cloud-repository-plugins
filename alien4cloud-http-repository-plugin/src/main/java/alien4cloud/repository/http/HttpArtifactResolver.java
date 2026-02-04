package alien4cloud.repository.http;

import static alien4cloud.repository.http.HttpUtil.isHttpURL;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Value;

import alien4cloud.component.repository.IArtifactResolver;
import alien4cloud.repository.model.ValidationResult;
import alien4cloud.repository.model.ValidationStatus;
import alien4cloud.repository.util.ResolverUtil;
import org.alien4cloud.tosca.normative.constants.NormativeCredentialConstant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpArtifactResolver implements IArtifactResolver {

    private Path tempDir;

    @Value("${directories.alien}/${directories.upload_temp}")
    public void setTempDir(String tempDir) throws IOException {
        this.tempDir = ResolverUtil.createPluginTemporaryDownloadDir(tempDir, "artifacts/http");
    }

    private CloseableHttpClient httpclient = HttpClients.custom().useSystemProperties().setRedirectStrategy(new DefaultRedirectStrategy()).build();

    @Override
    public String getResolverType() {
        return "http";
    }

    @Override
    public ValidationResult canHandleArtifact(String artifactReference, String repositoryURL, String repositoryType, Map<String, Object> credentials) {
        ValidationResult basicValidationResult = validateArtifact(artifactReference, repositoryURL, repositoryType, credentials);
        if (basicValidationResult.equals(ValidationResult.SUCCESS)) {
            try (InputStream ignored = downloadArtifact(artifactReference, repositoryURL, credentials)) {
                return ValidationResult.SUCCESS;
            } catch (IOException e) {
                return new ValidationResult(ValidationStatus.ARTIFACT_NOT_RETRIEVABLE, "Artifact cannot be retrieved " + e.getMessage());
            }
        } else {
            return basicValidationResult;
        }
    }

    ValidationResult validateArtifact(String artifactReference, String repositoryURL, String repositoryType, Map<String, Object> credentials) {
        if (!ResolverUtil.isResolverTypeCompatible(this, repositoryType)) {
            return new ValidationResult(ValidationStatus.INVALID_REPOSITORY_TYPE, "Repository is not of type " + getResolverType());
        }
        if (!ResolverUtil.isCredentialsBasicUserPassword(credentials)) {
            // Must have credentials as user:password
            return new ValidationResult(ValidationStatus.INVALID_CREDENTIALS, "Credentials must be in format [user:password]");
        }
        if (StringUtils.isNotBlank(repositoryURL)) {
            // If repository is defined then it must be a http url
            if (!isHttpURL(repositoryURL)) {
                return new ValidationResult(ValidationStatus.INVALID_REPOSITORY_URL, "Repository's URL is not HTTP compliant");
            }
            // Artifact's reference is mandatory
            if (StringUtils.isBlank(artifactReference)) {
                return new ValidationResult(ValidationStatus.INVALID_ARTIFACT_REFERENCE, "Wrong artifact reference, artifact cannot be null");
            }
            // If reference is a http url then it must have base path the repository's url
            if (isHttpURL(artifactReference) && !artifactReference.startsWith(repositoryURL)) {
                return new ValidationResult(ValidationStatus.INVALID_ARTIFACT_REFERENCE,
                        "Wrong artifact reference, artifact is an absolute http url but does not begin with repository's url");
            }
        } else {
            // Repository is blank then artifact reference must be a http url
            if (!isHttpURL(artifactReference)) {
                return new ValidationResult(ValidationStatus.INVALID_ARTIFACT_REFERENCE, "Repository's URL is not configured neither artifact reference");
            }
        }
        return ValidationResult.SUCCESS;
    }

    private InputStream downloadArtifact(String artifactReference, String repositoryURL, Map<String, Object> credentials) throws IOException {
        String getURL;
        if (isHttpURL(artifactReference)) {
            getURL = artifactReference;
        } else {
            getURL = repositoryURL;
            if (StringUtils.isNotBlank(artifactReference)) {
                getURL += "/" + artifactReference;
            }
        }
        HttpClientContext context = null;
        if (MapUtils.isNotEmpty(credentials)) {
            // Prepare basic login
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(credentials.get(NormativeCredentialConstant.USER_KEY).toString(),
                    credentials.get(NormativeCredentialConstant.TOKEN_KEY).toString()));
            // Add AuthCache to the execution context
            context = HttpClientContext.create();
            context.setCredentialsProvider(credentialsProvider);
        }
        // Launch HTTP Get
        HttpGet httpget = new HttpGet(getURL);
        CloseableHttpResponse response = httpclient.execute(httpget, context);
        if (response.getStatusLine().getStatusCode() < 200 || response.getStatusLine().getStatusCode() >= 300) {
            response.close();
            throw new IOException("Received status " + response.getStatusLine() + " while trying to download artifact at " + getURL);
        } else {
            return response.getEntity().getContent();
        }

    }

    String doResolveArtifact(String artifactReference, String repositoryURL, Map<String, Object> credentials) {
        try (InputStream artifactStream = downloadArtifact(artifactReference, repositoryURL, credentials)) {
            return ResolverUtil.copyArtifactStreamToTempFile(artifactReference, artifactStream, tempDir).toString();
        } catch (IOException e) {
            log.info("Error downloading artifact" + artifactReference + " at " + repositoryURL + " because of exception " + e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Error downloading artifact" + artifactReference + " at " + repositoryURL, e);
            }
            return null;
        }
    }

    @Override
    public String resolveArtifact(String artifactReference, String repositoryURL, String repositoryType, Map<String, Object> credentials) {
        if (!validateArtifact(artifactReference, repositoryURL, repositoryType, credentials).equals(ValidationResult.SUCCESS)) {
            return null;
        } else {
            return doResolveArtifact(artifactReference, repositoryURL, credentials);
        }
    }
}
