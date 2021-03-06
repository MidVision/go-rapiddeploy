package com.midvision.go.plugin.packagematerial;

import static com.midvision.go.plugin.packagematerial.JsonUtil.fromJsonString;
import static com.midvision.go.plugin.packagematerial.JsonUtil.toJsonString;
import static com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse.success;
import static java.util.Arrays.asList;

import java.util.LinkedHashMap;
import java.util.Map;

import com.midvision.go.plugin.packagematerial.message.CheckConnectionResultMessage;
import com.midvision.go.plugin.packagematerial.message.LatestPackageRevisionMessage;
import com.midvision.go.plugin.packagematerial.message.LatestPackageRevisionSinceMessage;
import com.midvision.go.plugin.packagematerial.message.PackageConnectionMessage;
import com.midvision.go.plugin.packagematerial.message.PackageRevisionMessage;
import com.midvision.go.plugin.packagematerial.message.RepositoryConnectionMessage;
import com.midvision.go.plugin.packagematerial.message.ValidatePackageConfigurationMessage;
import com.midvision.go.plugin.packagematerial.message.ValidateRepositoryConfigurationMessage;
import com.midvision.go.plugin.packagematerial.message.ValidationResultMessage;
import com.thoughtworks.go.plugin.api.AbstractGoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

@Extension
public class RapidDeployRepositoryMaterial extends AbstractGoPlugin {

	public static final String EXTENSION_NAME = "package-repository";
	public static final String REQUEST_REPOSITORY_CONFIGURATION = "repository-configuration";
	public static final String REQUEST_PACKAGE_CONFIGURATION = "package-configuration";
	public static final String REQUEST_VALIDATE_REPOSITORY_CONFIGURATION = "validate-repository-configuration";
	public static final String REQUEST_VALIDATE_PACKAGE_CONFIGURATION = "validate-package-configuration";
	public static final String REQUEST_CHECK_REPOSITORY_CONNECTION = "check-repository-connection";
	public static final String REQUEST_CHECK_PACKAGE_CONNECTION = "check-package-connection";
	public static final String REQUEST_LATEST_PACKAGE_REVISION = "latest-revision";
	public static final String REQUEST_LATEST_PACKAGE_REVISION_SINCE = "latest-revision-since";

	private final Map<String, MessageHandler> handlerMap = new LinkedHashMap<String, MessageHandler>();
	private final RapidDeployRepositoryConfigurationProvider configurationProvider;
	private final RapidDeployPackageMaterialPoller packageRepositoryPoller;

	public RapidDeployRepositoryMaterial() {
		configurationProvider = new RapidDeployRepositoryConfigurationProvider();
		packageRepositoryPoller = new RapidDeployPackageMaterialPoller(configurationProvider);
		handlerMap.put(REQUEST_REPOSITORY_CONFIGURATION, repositoryConfigurationsMessageHandler());
		handlerMap.put(REQUEST_PACKAGE_CONFIGURATION, packageConfigurationMessageHandler());
		handlerMap.put(REQUEST_VALIDATE_REPOSITORY_CONFIGURATION, validateRepositoryConfigurationMessageHandler());
		handlerMap.put(REQUEST_VALIDATE_PACKAGE_CONFIGURATION, validatePackageConfigurationMessageHandler());
		handlerMap.put(REQUEST_CHECK_REPOSITORY_CONNECTION, checkRepositoryConnectionMessageHandler());
		handlerMap.put(REQUEST_CHECK_PACKAGE_CONNECTION, checkPackageConnectionMessageHandler());
		handlerMap.put(REQUEST_LATEST_PACKAGE_REVISION, latestRevisionMessageHandler());
		handlerMap.put(REQUEST_LATEST_PACKAGE_REVISION_SINCE, latestRevisionSinceMessageHandler());
	}

	@Override
	public GoPluginApiResponse handle(final GoPluginApiRequest goPluginApiRequest) {
		try {
			if (handlerMap.containsKey(goPluginApiRequest.requestName())) {
				return handlerMap.get(goPluginApiRequest.requestName()).handle(goPluginApiRequest);
			}
			return DefaultGoPluginApiResponse.badRequest(String.format("Invalid request name %s", goPluginApiRequest.requestName()));
		} catch (final Throwable e) {
			return DefaultGoPluginApiResponse.error(e.getMessage());
		}
	}

	@Override
	public GoPluginIdentifier pluginIdentifier() {
		return new GoPluginIdentifier(EXTENSION_NAME, asList("1.0"));
	}

	MessageHandler packageConfigurationMessageHandler() {
		return new MessageHandler() {
			@Override
			public GoPluginApiResponse handle(final GoPluginApiRequest request) {
				return success(toJsonString(configurationProvider.packageConfiguration().getPropertyMap()));
			}
		};

	}

	MessageHandler repositoryConfigurationsMessageHandler() {
		return new MessageHandler() {
			@Override
			public GoPluginApiResponse handle(final GoPluginApiRequest request) {
				return success(toJsonString(configurationProvider.repositoryConfiguration().getPropertyMap()));
			}
		};
	}

	MessageHandler validateRepositoryConfigurationMessageHandler() {
		return new MessageHandler() {
			@Override
			public GoPluginApiResponse handle(final GoPluginApiRequest request) {

				final ValidateRepositoryConfigurationMessage message = fromJsonString(request.requestBody(), ValidateRepositoryConfigurationMessage.class);
				final ValidationResultMessage validationResultMessage = configurationProvider
						.validateRepositoryConfiguration(message.getRepositoryConfiguration());
				if (validationResultMessage.failure()) {
					return success(toJsonString(validationResultMessage.getValidationErrors()));
				}
				return success("");
			}
		};
	}

	MessageHandler validatePackageConfigurationMessageHandler() {
		return new MessageHandler() {
			@Override
			public GoPluginApiResponse handle(final GoPluginApiRequest request) {
				final ValidatePackageConfigurationMessage message = fromJsonString(request.requestBody(), ValidatePackageConfigurationMessage.class);
				final ValidationResultMessage validationResultMessage = configurationProvider.validatePackageConfiguration(message.getPackageConfiguration());
				if (validationResultMessage.failure()) {
					return success(toJsonString(validationResultMessage.getValidationErrors()));
				}
				return success("");
			}
		};
	}

	MessageHandler checkRepositoryConnectionMessageHandler() {
		return new MessageHandler() {
			@Override
			public GoPluginApiResponse handle(final GoPluginApiRequest request) {
				final RepositoryConnectionMessage message = fromJsonString(request.requestBody(), RepositoryConnectionMessage.class);
				final CheckConnectionResultMessage result = packageRepositoryPoller.checkConnectionToRepository(message.getRepositoryConfiguration());
				return success(toJsonString(result));
			}
		};
	}

	MessageHandler checkPackageConnectionMessageHandler() {
		return new MessageHandler() {
			@Override
			public GoPluginApiResponse handle(final GoPluginApiRequest request) {
				final PackageConnectionMessage message = fromJsonString(request.requestBody(), PackageConnectionMessage.class);
				final CheckConnectionResultMessage result = packageRepositoryPoller.checkConnectionToPackage(message.getPackageConfiguration(),
						message.getRepositoryConfiguration());
				return success(toJsonString(result));
			}
		};
	}

	MessageHandler latestRevisionMessageHandler() {
		return new MessageHandler() {
			@Override
			public GoPluginApiResponse handle(final GoPluginApiRequest request) {
				final LatestPackageRevisionMessage message = fromJsonString(request.requestBody(), LatestPackageRevisionMessage.class);
				final PackageRevisionMessage revision = packageRepositoryPoller.getLatestRevision(message.getPackageConfiguration(),
						message.getRepositoryConfiguration());
				return success(toJsonString(revision));
			}
		};
	}

	MessageHandler latestRevisionSinceMessageHandler() {
		return new MessageHandler() {
			@Override
			public GoPluginApiResponse handle(final GoPluginApiRequest request) {
				final LatestPackageRevisionSinceMessage message = fromJsonString(request.requestBody(), LatestPackageRevisionSinceMessage.class);
				final PackageRevisionMessage revision = packageRepositoryPoller.getLatestRevisionSince(message.getPackageConfiguration(),
						message.getRepositoryConfiguration(), message.getPreviousRevision());
				return success(revision == null ? null : toJsonString(revision));
			}
		};
	}
}
