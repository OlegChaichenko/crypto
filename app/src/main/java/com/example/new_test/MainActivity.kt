package com.example.new_test

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

class MainActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var coinDao: CoinDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "coins_database"
        ).build()
        coinDao = database.coinDao()

        val coinListTextView: TextView = findViewById(R.id.coinList)
        coinListTextView.textSize = 18f


        val retrofit = Retrofit.Builder()
            .baseUrl("https://min-api.cryptocompare.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(CryptoApi::class.java)
        val call = api.getCryptoData()


        call.enqueue(object : Callback<CryptoResponse> {
            override fun onResponse(call: Call<CryptoResponse>, response: Response<CryptoResponse>) {
                if (response.isSuccessful) {
                    val coins = response.body()?.Data ?: emptyList()


                    lifecycleScope.launch(Dispatchers.IO) {
                        val coinEntities = coins.map { coin ->
                            CoinEntity(
                                id = coin.CoinInfo.Name,
                                name = coin.CoinInfo.Name,
                                fullName = coin.CoinInfo.FullName,
                                price = coin.RAW?.USD?.PRICE,
                                algorithm = coin.CoinInfo.Algorithm,
                                launchDate = coin.CoinInfo.AssetLaunchDate
                            )
                        }
                        coinDao.insertCoins(coinEntities)


                        val storedCoins = coinDao.getAllCoins()
                        withContext(Dispatchers.Main) {
                            val coinsString = storedCoins.joinToString("\n\n") { coin ->
                                """
                                Название: ${coin.name}
                                Полное название: ${coin.fullName}
                                Цена: ${coin.price ?: "N/A"} USD
                                Алгоритм: ${coin.algorithm}
                                Дата запуска: ${coin.launchDate ?: "Неизвестно"}
                                """.trimIndent()
                            }
                            coinListTextView.text = coinsString
                        }
                    }
                } else {
                    Log.e("API Error", "Error: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<CryptoResponse>, t: Throwable) {
                Log.e("API Error", "Failure: ${t.message}")
            }
        })
    }
}


interface CryptoApi {
    @GET("data/top/totalvolfull?tsym=USD")
    fun getCryptoData(): Call<CryptoResponse>
}


data class CryptoResponse(
    val Data: List<CryptoData>
)

data class CryptoData(
    val CoinInfo: CoinInfo,
    val RAW: Raw?
)

data class CoinInfo(
    val Name: String,
    val FullName: String,
    val Algorithm: String,
    val AssetLaunchDate: String?
)

data class Raw(
    val USD: USD?
)

data class USD(
    val PRICE: String?
)


@Entity(tableName = "coins")
data class CoinEntity(
    @PrimaryKey val id: String,
    val name: String,
    val fullName: String,
    val price: String?,
    val algorithm: String,
    val launchDate: String?
)


@Dao
interface CoinDao {
    @Query("SELECT * FROM coins")
    fun getAllCoins(): List<CoinEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCoins(coins: List<CoinEntity>)
}


@Database(entities = [CoinEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun coinDao(): CoinDao
}
