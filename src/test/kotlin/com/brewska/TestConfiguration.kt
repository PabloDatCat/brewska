package com.brewska

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigResolveOptions

internal object TestConfiguration {
    // Resolve the real optional substitutions using only the supplied environment.
    // Do not read system properties, external config.file, or the developer's environment.
    fun resource(environment: Map<String, String> = emptyMap()): Config =
        ConfigFactory.parseResources("application.conf")
            .withFallback(ConfigFactory.parseMap(environment))
            .resolve(ConfigResolveOptions.defaults().setUseSystemEnvironment(false))
}
