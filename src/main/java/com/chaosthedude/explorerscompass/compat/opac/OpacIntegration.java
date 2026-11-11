package com.chaosthedude.explorerscompass.compat.opac;

import com.chaosthedude.explorerscompass.unlocking.PartySharing;
import xaero.pac.common.server.api.OpenPACServerAPI;
import java.util.stream.Collectors;

/** Loaded only when OPAC is installed. No claims or allied-party access is shared. */
public final class OpacIntegration {
    private OpacIntegration() {}
    public static void register() {
        PartySharing.register(server -> OpenPACServerAPI.get(server).getPartyManager().getAllStream()
                .collect(Collectors.toMap(party -> party.getId(),
                        party -> party.getMemberInfoStream().map(member -> member.getUUID())
                                .collect(Collectors.toUnmodifiableSet()))));
    }
}
