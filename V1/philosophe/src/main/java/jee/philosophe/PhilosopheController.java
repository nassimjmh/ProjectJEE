package jee.philosophe;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/philosophe")
public class PhilosopheController {

    @Autowired
    private PhilosopheService philosopheService;

    @Value("${philosopher.id}")
    private int philosopherId;

    @GetMapping("/fourchette")
    public boolean demanderFourchette() {
        return philosopheService.preterFourchette();
    }

    @PostMapping("/rendre-fourchette")
    public void rendreFourchette() {
        philosopheService.recupererFourchette();
    }


    @PostMapping
    public PhilosopheEvent philosopheEvent(@RequestBody PhilosopheEvent philosopheEvent){
        return philosopheEvent;
    }

}