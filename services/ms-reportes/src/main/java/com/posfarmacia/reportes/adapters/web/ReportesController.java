package com.posfarmacia.reportes.adapters.web;

import com.posfarmacia.reportes.usecases.port.out.ReadModelPort;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultas del tablero.
 *
 * <p>Todas leen tablas ya desnormalizadas. Lo que en el monolito era un JOIN de tres
 * tablas contra la base de ventas, aqui es un SELECT sobre una tabla plana y encima
 * corre mas rapido. Esa es la compensacion completa de haber perdido el JOIN entre
 * contextos al partir el sistema.
 */
@RestController
@RequestMapping("/api/reportes")
public class ReportesController {

    private final ReadModelPort readModel;

    public ReportesController(ReadModelPort readModel) {
        this.readModel = readModel;
    }

    /**
     * Ventas diarias.
     *
     * <p>Acepta {@code fecha} (un dia, que es lo que pide la pantalla) o el rango
     * {@code desde}/{@code hasta}. Sin nada, devuelve el dia de hoy: un reporte que
     * exige tres parametros para responder algo es un reporte que nadie abre.
     */
    @GetMapping("/ventas-diarias")
    public ResponseEntity<List<ReadModelPort.VentaDiaria>> ventasDiarias(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) UUID localId) {
        LocalDate inicio = desde != null ? desde : (fecha != null ? fecha : LocalDate.now());
        LocalDate fin = hasta != null ? hasta : (fecha != null ? fecha : LocalDate.now());
        return ResponseEntity.ok(readModel.ventasDiarias(inicio, fin, localId));
    }

    @GetMapping("/incentivos")
    public ResponseEntity<List<ReadModelPort.IncentivoVendedor>> incentivos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(readModel.incentivos(desde, hasta));
    }
}
