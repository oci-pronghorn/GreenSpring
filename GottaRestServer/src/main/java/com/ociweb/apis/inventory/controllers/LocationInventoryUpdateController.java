package com.ociweb.apis.inventory.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/inventory/")
public class LocationInventoryUpdateController extends BaseController {
	@RequestMapping(value = "/{orgCode}/{feedType}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Response> createInventory(@RequestBody List<InventoryStoreMulti> inventorydata,
			@PathVariable String orgCode, @PathVariable int feedType) {
		return new ResponseEntity<Response>(HttpStatus.OK);
	}

	@RequestMapping(value = "/" + Constants.OVERRIDE_DEMAND + "/{orgCode}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Response> createOverrideDemand(@RequestBody List<OverrideDemand> overrideDemandList,
			@PathVariable String orgCode) {
		return new ResponseEntity<Response>(HttpStatus.OK);
	}
}


