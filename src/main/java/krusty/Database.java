package krusty;

import spark.Request;
import spark.Response;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static krusty.Jsonizer.toJson;

public class Database {
	/**
	 * Modify it to fit your environment and then use this string when connecting to your database!
	 */
	private static final String jdbcString = "jdbc:mysql://puccini.cs.lth.se/hbg20";

	private static final String jdbcUsername = "hbg20";
	private static final String jdbcPassword = "ynm704yj";

	protected Connection connection = null;

	public void connect() {
		try {
			connection= DriverManager.getConnection(jdbcString,jdbcUsername,jdbcPassword);

		} catch (SQLException ex) {
			System.out.println("SQLException: " + ex.getMessage());
			System.out.println("SQLState: " + ex.getSQLState());
			System.out.println("VendorError: " + ex.getErrorCode());
		}
	}

	public String getCustomers(Request req, Response res) {
		try {
			String sql="select name, adress as address from Customers";
			PreparedStatement stm= connection.prepareStatement(sql);
			ResultSet rs= stm.executeQuery();
			return toJson(rs, "customers");
		}catch (SQLException e){
			throw new RuntimeException(e);
		}
	}

	public String getRawMaterials(Request req, Response res) {
		try {
			String sql="select materialName as name, amount, unit from RawMaterials";
			PreparedStatement stm= connection.prepareStatement(sql);
			ResultSet rs= stm.executeQuery();
			return Jsonizer.toJson(rs, "raw-materials");
		} catch (SQLException e){
			throw new RuntimeException(e);
		}
	}

	public String getCookies(Request req, Response res) {

		try {
			String sql="select cookieName as name from Cookies";
			PreparedStatement stm= connection.prepareStatement(sql);
			ResultSet rs= stm.executeQuery();
			return Jsonizer.toJson(rs, "cookies");
		} catch (SQLException e){
			throw new RuntimeException(e);
		}
	}

	public String getRecipes(Request req, Response res) {
		try {
			String sql="select cookieName as cookie, materialName as raw-material, amount, unit" +
					"from Recipes innre join RawMaterials using (materialName)";
			PreparedStatement stm= connection.prepareStatement(sql);
			ResultSet rs= stm.executeQuery();
			return Jsonizer.toJson(rs, "recipes");
			//return "{\"cookies\":[]}";
		} catch (SQLException e){
			throw new RuntimeException(e);
		}
	}

	public String getPallets(Request req, Response res) {

		try {
		StringBuilder sb= new StringBuilder();
		sb.append("select palletId as id, cookieName as cookie, productionDate as production_date , name as customer, IF(isBlocked=1, 'yes', 'no') as blocked\n" +
				"from Pallets\n" +
				"left outer join Status using (palletId)\n" +
				"left outer join Orders using (orderId)\n");

            int counter=0;
			if(req.queryParams("from")!=null||req.queryParams("to")!=null||req.queryParams("cookie")!=null||req.queryParams("blocked")!=null){
				sb.append(" where ");
			}
			if (req.queryParams("from") != null) {
				sb.append(" productionDate >= '"+req.queryParams("from")+"' ");
				counter++;
			}
			if (req.queryParams("to") != null) {
				if (counter!=0){
					sb.append(" and ");
				}
				sb.append(" productionDate <= '"+req.queryParams("to")+"' ");
				counter++;
			}
			if (req.queryParams("cookie") != null) {
				if (counter!=0){
					sb.append(" and ");
				}
				sb.append(" cookieName= '"+req.queryParams("cookie")+"' ");
				counter++;
			}
			if (req.queryParams("blocked") != null) {
				if (counter!=0){
					sb.append(" and ");
				}
				if (req.queryParams("blocked")=="yes"){
					sb.append(" isBlocked= 1 ");
				}else{
					sb.append(" isBlocked= 0 ");
				}
			}
		 sb.append(" order by  productionDate desc \n");

			PreparedStatement stmt= connection.prepareStatement(sb.toString());
			ResultSet rs= stmt.executeQuery();
			return Jsonizer.toJson(rs, "pallets");

		} catch (SQLException e){
			throw new RuntimeException(e);
		}
	}

	public String reset(Request req, Response res) {
		try {
			Statement stmt = connection.createStatement();

			InputStream resourceAsStream = getClass().getResource("/reset.sql").openStream();
			if (resourceAsStream == null)
				throw new IOError(new IOException("Could not find reset.sql"));

			BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream, StandardCharsets.UTF_8));
			reader.lines()
					.filter(line -> !line.trim().startsWith("--") || !line.trim().isEmpty())
					.forEach(line -> {
						try {
							stmt.addBatch(line.trim());
						} catch (SQLException e) {
							System.out.println("Line: " + line);
							e.printStackTrace();
						}
					});

			stmt.executeBatch();
			stmt.close();
			return "{\n" +
					"  \"status\": \"ok\"\n" +
					"}";
		}
		catch (IOException e) {
			throw new RuntimeException("Could not open reset file.");
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public String createPallet(Request req, Response res) {
		try {
			String sql="select cookieName from Cookies";
			String cookie="";
			if (req.queryParams("cookie") != null) {
				cookie= req.queryParams("cookie");
			}

			PreparedStatement stm= connection.prepareStatement(sql);
			ResultSet rs= stm.executeQuery();
			while (rs.next()){

				if (rs.getString("cookieName").equals(cookie)){
					updateRawMaterials(cookie);

					String query ="insert into Pallets (cookieName , productionDate)\n" +
							"values ('"+cookie+"', '"+java.time.LocalDate.now()+"') ";
					PreparedStatement statement= connection.prepareStatement(query);
					statement.executeUpdate(query, Statement.RETURN_GENERATED_KEYS);
					ResultSet rs1 = statement.getGeneratedKeys();

					return"{\n" +
							"  \"status\": \"ok\",\n" +
							"  \"id\": "+rs1.getInt("1")+"\n" +
							"}";

				}
			}
			return "{\n" +
					"  \"status\": \"unknown cookie\"\n" +
					"}";
		} catch (SQLException e){
			return "{\n" +
					"  \"status\": \"error\"\n" +
					"}";
		}
	}

	private void updateRawMaterials(String cookie) {
		try {
			String sql="select * from Recipes where cookieName='"+cookie+"' ";
			PreparedStatement stm= connection.prepareStatement(sql);
			ResultSet rs= stm.executeQuery();
			while(rs.next()){
				String material= rs.getString("materialName");
				int amount= rs.getInt("amount");
				amount*=54;
				String query="update RawMaterials set lastAmount= lastAmount- "+amount+", amount= amount-"+amount+", lastModified='"+java.time.LocalDate.now()+"'" +
						" where materialName='"+material+"';";
				PreparedStatement stmt= connection.prepareStatement(query);
				stmt.executeUpdate();
			}
		}catch (SQLException e){
			throw new RuntimeException();
		}
	}
}
