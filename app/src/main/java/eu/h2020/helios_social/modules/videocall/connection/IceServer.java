/*************************************************************************
 *
 * ATOS CONFIDENTIAL
 * __________________
 *
 *  Copyright (2020) Atos Spain SA
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Atos Spain SA and other companies of the Atos group.
 * The intellectual and technical concepts contained
 * herein are proprietary to Atos Spain SA
 * and other companies of the Atos group and may be covered by Spanish regulations
 * and are protected by copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Atos Spain SA.
 */
package eu.h2020.helios_social.modules.videocall.connection;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class IceServer {

    @SerializedName("url")
    @Expose
    public String url;
    @SerializedName("username")
    @Expose
    public String username;
    @SerializedName("credential")
    @Expose
    public String credential;

    // This constructor was added because it was not present in the original example code
    public IceServer(String url, String username, String credential) {
        this.url = url;
        this.username = username;
        this.credential = credential;
    }

    @Override
    public String toString() {
        return "IceServer{" +
                "url='" + url + '\'' +
                ", username='" + username + '\'' +
                ", credential='" + credential + '\'' +
                '}';
    }
}