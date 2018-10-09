package ch.hsr.dsl.dwrtc.signaling

import ch.hsr.dsl.dwrtc.signaling.exceptions.ClientNotFoundException
import ch.hsr.dsl.dwrtc.util.buildNewPeer
import ch.hsr.dsl.dwrtc.util.findFreePort
import mu.KLogging
import net.tomp2p.peers.Number160
import net.tomp2p.peers.PeerAddress
import java.util.*

class ClientService() {
    companion object : KLogging()

    private val port = findFreePort()
    private val peerId = UUID.randomUUID().toString()
    internal val peer = buildNewPeer(peerId)

    init {
        logger.info { "creating service with peer id $peerId port $port" }
    }

    constructor(bootstrapPeerAddress: PeerConnectionDetails) : this() {
        logger.info { "using bootstrap peer $bootstrapPeerAddress" }

        peer.peer().bootstrap().inetAddress(bootstrapPeerAddress.ipAddress).ports(bootstrapPeerAddress.port).start()
                .awaitListeners()
    }

    constructor(bootstrapPeerAddress: PeerAddress) : this() {
        logger.info { "using bootstrap peer (TomP2P format) $bootstrapPeerAddress" }

        peer.peer().bootstrap().peerAddress(bootstrapPeerAddress).start()
                .awaitListeners()
    }

    fun addClient(sessionId: String): InternalClient {
        logger.info { "add client $sessionId" }
        logger.info { "own peer: ${peer.peerAddress()} " }

        peer.put(Number160.createHash(sessionId)).`object`(peer.peerAddress()).start().awaitUninterruptibly()
        return InternalClient(peer, sessionId)
    }

    fun removeClient(internalClient: InternalClient) {
        logger.info { "remove client ${internalClient.sessionId}" }

        peer.remove(Number160.createHash(internalClient.sessionId)).all().start().awaitUninterruptibly()
    }

    fun findClient(sessionId: String): ExternalClient {
        logger.info { "try to find client $sessionId" }

        val peerIdGet = peer.get(Number160.createHash(sessionId)).start().awaitUninterruptibly()
        return if (peerIdGet.isSuccess && peerIdGet.data() != null) {
            logger.info { "found client" }

            val peerAddress = peerIdGet.data().`object`() as PeerAddress
            ExternalClient(sessionId, peerAddress)
        } else throw ClientNotFoundException("No peer found under session ID $sessionId")
    }
}