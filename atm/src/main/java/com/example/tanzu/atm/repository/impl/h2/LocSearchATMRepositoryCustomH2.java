package com.example.tanzu.atm.repository.impl.h2;

import org.h2gis.functions.spatial.convert.ST_GeomFromText;
import org.springframework.r2dbc.core.DatabaseClient;

import com.example.tanzu.atm.entity.ATM;
import com.example.tanzu.atm.repository.impl.LocSearchATMRepositoryCustom;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LocSearchATMRepositoryCustomH2 implements LocSearchATMRepositoryCustom
{

	protected DatabaseClient client;
	
	
	public LocSearchATMRepositoryCustomH2(DatabaseClient client)
	{
		this.client = client;
	}	
	
	@Override
	public Flux<ATM> findByLocationDistance(float latitude, float longitude, int radius) 
	{
		try
		{
			final var wkt = String.format("POINT(%f %f)", latitude, longitude);
			
			final var geoPt = ST_GeomFromText.toGeometry(wkt, 4326);			
			
			/*
			 * Rough geo location distance.  Will get within .1 miles of accuracy in most search which
			 * is more than adequate for our purposes.
			 */
			var geoRadius = (float)radius / 69.0f;
			
			return client.sql("SELECT *, ST_Distance(atm.cord, $1) AS dist from atm GROUP BY atm.id HAVING dist <= $2 ")
			    .bind("$1", geoPt)
			    .bind("$2", geoRadius)
			    .map(row -> {
				  return new ATM(row.get("id", Long.class), (String)row.get("name", String.class), row.get("latitude", Float.class), 
						  row.get("longitude", Float.class), row.get("addr", String.class), row.get("city", String.class), row.get("state", String.class), 
						  row.get("postalCode", String.class), row.get("dist", Double.class) * 69.0, row.get("inDoors", Boolean.class),
						  row.get("branchId", Long.class));
				  }).all();
		}
		catch (Exception e)
		{
			return Flux.error(e);
		}
	}
	
	@Override
	public Mono<ATM> saveATM(ATM atm)
	{
		final var wkt = String.format("POINT(%f %f)", atm.latitude(), atm.longitude());
		
		try
		{
			final var insert = "insert into atm (name, addr, city, state, postalCode, inDoors, latitude, longitude, cord, branchId)  values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)";
			
			final var geoPt = ST_GeomFromText.toGeometry(wkt, 4326);
			
			return
				  client.sql(insert)
				  .filter((statement, executeFunction) -> statement.returnGeneratedValues("id").execute())
				  .bind("$1", atm.name())
			      .bind("$2", atm.addr())
			      .bind("$3", atm.city())
			      .bind("$4", atm.state())
			      .bind("$5", atm.postalCode())
			      .bind("$6", atm.inDoors())
			      .bind("$7", atm.latitude())
			      .bind("$8", atm.longitude())
			      .bind("$9", geoPt)
			      .bindNull("$10", Long.class)
			      .fetch()
			      .first()
			      .map(row -> {
					  
					  return new ATM((Long)row.get("id"), 
							  atm.name(), 
							  atm.latitude(), 
							  atm.longitude(),
							  atm.addr(), 
							  atm.city(), 
							  atm.state(), 
							  atm.postalCode(), 
							  atm.distance(), 
							  atm.inDoors(),
							  atm.branchId());
				  });
					
		}
		catch (Exception e)
		{
			return Mono.error(e);
		}		

	}

}
