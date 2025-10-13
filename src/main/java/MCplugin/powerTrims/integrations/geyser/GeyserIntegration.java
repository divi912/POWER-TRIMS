package MCplugin.powerTrims.integrations.geyser;

import java.util.UUID;

public interface GeyserIntegration {
    boolean isBedrockPlayer(UUID playerUUID);
}