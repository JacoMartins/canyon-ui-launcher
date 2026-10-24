package io.github.jacomartins.canyonui.search

import io.github.jacomartins.canyonui.search.Command.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandsTest {

    private fun parse(s: String) = Commands.parse(s)

    @Test fun calls() {
        assertEquals(Command.Call("Ana"), parse("call Ana"))
        assertEquals(Command.Call("ana souza"), parse("CALL ana souza"))
        assertEquals(Command.Call("Ana"), parse("ligar para Ana"))
        assertEquals(Command.Call("ana", Via.WHATSAPP), parse("whatsapp call ana"))
        assertEquals(Command.Call("ana", Via.WHATSAPP), parse("zap ligar pra ana"))
        assertEquals(Command.VideoCall("ana"), parse("video call ana"))
        assertEquals(Command.VideoCall("ana"), parse("videochamada com ana"))
    }

    @Test fun messages() {
        assertEquals(Command.Message("ana"), parse("message ana"))
        assertEquals(Command.Message("ana"), parse("mensagem pra ana"))
        assertEquals(Command.Message("ana", Via.WHATSAPP), parse("zap ana"))
        assertEquals(Command.Message("Ana", Via.WHATSAPP), parse("WhatsApp Ana"))
        assertEquals(Command.Message("ana", Via.SMS), parse("text ana"))
        assertEquals(Command.Email("ana"), parse("email ana"))
    }

    @Test fun inApp() {
        assertEquals(Command.InApp("stories", "instagram"), parse("stories on instagram"))
        assertEquals(Command.InApp("stories", "instagram"), parse("stories no instagram"))
        assertEquals(Command.Play("love on top", "spotify"), parse("play love on top on spotify"))
        assertEquals(Command.Play("jazz", null), parse("tocar jazz"))
    }

    @Test fun clock() {
        assertEquals(Command.Timer(300), parse("timer 5 min"))
        assertEquals(Command.Timer(300), parse("5 minute timer"))
        assertEquals(Command.Timer(5400), parse("timer 1h30"))
        assertEquals(Command.Timer(4800), parse("set a timer for 1 hour 20 minutes"))
        assertEquals(Command.Timer(90), parse("temporizador 90 segundos"))
        assertEquals(Command.Alarm(7, 30), parse("alarm 7:30"))
        assertEquals(Command.Alarm(19, 0), parse("alarm 7 pm"))
        assertEquals(Command.Alarm(6, 15), parse("despertador 6h15"))
    }

    @Test fun directions() {
        assertEquals(Command.Directions("Av. Paulista 1000"), parse("directions to Av. Paulista 1000"))
        assertEquals(Command.Directions("shopping"), parse("como chegar no shopping"))
    }

    @Test fun plainTextIsNoCommand() {
        assertNull(parse("ana"))
        assertNull(parse("whatsapp"))
        assertNull(parse("calculator"))
        assertNull(parse("timer"))
        assertEquals("camera", Commands.appQuery("open camera"))
        assertEquals("camera", Commands.appQuery("camera"))
    }

    @Test fun durations() {
        assertEquals(600, Commands.duration("10"))
        assertNull(Commands.duration("ten mangos"))
        assertNull(Commands.duration("5 mango"))
    }

    @Test fun addresses() {
        assertEquals("Rua Augusta, 1500", QueryKind.address("Rua Augusta, 1500"))
        assertEquals("Av. Paulista 1000", QueryKind.address("Av. Paulista 1000"))
        assertEquals("1600 Amphitheatre Parkway", QueryKind.address("1600 Amphitheatre Parkway"))
        assertEquals("Praça da Sé 10, 01001-000", QueryKind.address("Praça da Sé 10, 01001-000"))
        assertNull(QueryKind.address("rua"))
        assertNull(QueryKind.address("ruana"))
        assertNull(QueryKind.address("whatsapp"))
        assertNull(QueryKind.address("12345"))
    }
}
