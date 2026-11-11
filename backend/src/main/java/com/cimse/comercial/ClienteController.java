package com.cimse.comercial;

import com.cimse.config.ApiUtil;
import com.cimse.config.Validaciones;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteRepository repo;

    public ClienteController(ClienteRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Cliente> listar() {
        return repo.findAll();
    }

    /** Alta directa (la pantalla usa /registrar, que ademas guarda las personas de enlace). Valida igual. */
    @PostMapping
    public Cliente crear(@RequestBody Cliente cliente) {
        if (cliente.getNombre() == null || cliente.getNombre().isBlank()) {
            throw ApiUtil.error("El campo nombre es obligatorio.");
        }
        if (cliente.getDireccion() == null || cliente.getDireccion().isBlank()) {
            throw ApiUtil.error("El campo direccion es obligatorio.");
        }
        cliente.setRtn(Validaciones.rtn(cliente.getRtn()));
        cliente.setTelefono(Validaciones.telefono(cliente.getTelefono(), "telefono celular"));
        cliente.setCorreo(Validaciones.correo(cliente.getCorreo()));
        cliente.setIdCliente(null);
        return repo.save(cliente);
    }
}
