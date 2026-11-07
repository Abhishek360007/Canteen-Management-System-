package com.tulas.canteen.domain;

import jakarta.persistence.*;

/** Small key/value store for generated secrets that must survive restarts (e.g. the Web Push VAPID keys). */
@Entity
@Table(name = "app_secrets")
public class AppSecret {

    @Id
    @Column(name = "secret_key", length = 40)
    private String key;

    @Column(name = "secret_value", nullable = false, length = 200)
    private String value;

    public AppSecret() {}

    public AppSecret(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
}
