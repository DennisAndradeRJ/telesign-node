package com.forgerock.edu.auth.nodes.utilities;

import org.apache.commons.io.IOUtils;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;

@Singleton
public final class ClientScriptUtilities {

    private final Logger logger = LoggerFactory.getLogger("amAuth");

    /**
     * Gets a JavaScript script as a String.
     *
     * @param scriptFileName the filename of the script.
     * @return the script as an executable string.
     * @throws NodeProcessException if the file doesn't exist.
     */
    public String getScriptAsString(String scriptFileName) throws NodeProcessException {
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(scriptFileName);
        String script;
        try {
            script = IOUtils.toString(resourceStream, "UTF-8");
        } catch (IOException e) {
            logger.error("Failed to get the script, fatal error!", e);
            throw new NodeProcessException(e);
        }
        return script;
    }
}
