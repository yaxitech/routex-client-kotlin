# Module settlement

Confidential-computing key settlement and remote attestation for the YAXI Routex client.

Pulled in transitively by [routex-client][tech.yaxi.routex.client.RoutexClient] and [routex-refresh-client][tech.yaxi.routex.refresh.RoutexRefreshClient]; both clients own the lifecycle of the underlying session.
Direct construction of [Settlement][tech.yaxi.routex.settlement.Settlement] is not part of the supported surface.

# Package tech.yaxi.routex.settlement

End-to-end-encrypted session establishment with the routex TEE: client-side payload sealing, remote-attestation report parsing and verification, and the per-ticket key-settlement that gates every Routex request.
