package jee.philosophe.distributed;

import jee.philosophe.distributed.Messages.DoneMsg;
import jee.philosophe.distributed.Messages.TokenMsg;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/philosopher")
public class DistributedController {

    private final DistributedProtocolService protocol;

    public DistributedController(DistributedProtocolService protocol) {
        this.protocol = protocol;
    }

    @PostMapping("/request")
    public void request(@RequestBody TokenRequest msg) { protocol.onRequest(msg); }

    @PostMapping("/done")
    public void done(@RequestBody DoneMsg msg) { protocol.onDone(msg); }

    @PostMapping("/token")
    public void token(@RequestBody TokenMsg msg) { protocol.onToken(msg); }

    @GetMapping("/state")
    public Map<String, Object> state() { return protocol.snapshot(); }
}
