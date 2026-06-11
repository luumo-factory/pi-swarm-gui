package ai.luumo.tools.pi.piswarm.gui.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

/**
 * A reference to a model: provider + id, with an optional human-friendly name.
 * Mirrors the {@code {provider, id, name}} shape used on the MQTT registry topic.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ModelRef {

    private String provider;
    private String id;
    private String name;

    public ModelRef() {
    }

    public ModelRef(String provider, String id, String name) {
        this.provider = provider;
        this.id = id;
        this.name = name;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Best label for display: name, else id, else provider/id. */
    public String displayLabel() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (id != null && !id.isBlank()) {
            return id;
        }
        return provider == null ? "unknown" : provider;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ModelRef other)) {
            return false;
        }
        return Objects.equals(provider, other.provider) && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, id);
    }

    @Override
    public String toString() {
        return displayLabel();
    }
}
