/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wildfly.channel;

import java.io.File;
import java.net.URL;
import org.wildfly.channel.spi.MavenVersionsResolver;

/**
 *
 * @author jdenise
 */
public interface ArtifactChecker {
    void check(URL url) throws SignatureException;
    void check(ChannelMetadataCoordinate coordinate, String version, File resolved, MavenVersionsResolver resolver) throws SignatureException;
}
