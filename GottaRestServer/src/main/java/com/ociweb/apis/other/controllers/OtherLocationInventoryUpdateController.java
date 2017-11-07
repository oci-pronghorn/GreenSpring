package com.ociweb.apis.other.controllers;

import com.ociweb.apis.model.*;
import com.ociweb.greenspring.annotations.GreenServiceScope;
import com.ociweb.greenspring.annotations.GreenParallelism;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/otherinventory/")
@GreenParallelism(parallelBehavior = true, parallelRoutes = true, serviceScope = GreenServiceScope.route)
public class OtherLocationInventoryUpdateController extends BaseController {
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
