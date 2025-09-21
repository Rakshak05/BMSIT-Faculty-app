package com.bmsit.faculty

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

// We declare that AdminFragment will follow the rules of the OnItemClickListener
class AdminFragment : Fragment(), UserAdapter.OnItemClickListener {

    private lateinit var db: FirebaseFirestore
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var userAdapter: UserAdapter
    private val userList = mutableListOf<User>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        usersRecyclerView = view.findViewById(R.id.usersRecyclerView)
        usersRecyclerView.layoutManager = LinearLayoutManager(context)

        // When we create the adapter, we pass 'this' as the listener.
        // This tells the adapter, "When an item is clicked, notify this fragment."
        userAdapter = UserAdapter(userList, this)
        usersRecyclerView.adapter = userAdapter

        fetchUsers()
    }

    // This is the function that runs when the adapter tells us an item was clicked.
    override fun onItemClick(user: User) {
        // When a user row is tapped, we call the function to show the pop-up.
        showRoleChangeDialog(user)
    }

    // This new function creates and shows the pop-up dialog for changing roles.
    private fun showRoleChangeDialog(user: User) {
        val roles = arrayOf("Faculty", "HOD", "ADMIN")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Change Role for ${user.name}")
            .setItems(roles) { dialog, which ->
                // 'which' tells us which item was selected (0 for "Faculty", 1 for "HOD", etc.)
                val newRole = roles[which]
                updateUserRole(user, newRole)
            }
        builder.create().show()
    }

    // This new function handles the actual database update.
    private fun updateUserRole(user: User, newRole: String) {
        db.collection("users").document(user.uid)
            .update("role", newRole)
            .addOnSuccessListener {
                Toast.makeText(context, "${user.name}'s role updated to $newRole", Toast.LENGTH_SHORT).show()
                // We fetch the users again to instantly show the change in the list.
                fetchUsers()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error updating role", Toast.LENGTH_SHORT).show()
                Log.w("AdminFragment", "Error updating document", e)
            }
    }


    private fun fetchUsers() {
        db.collection("users")
            .get()
            .addOnSuccessListener { result ->
                userList.clear()
                for (document in result) {
                    val user = document.toObject(User::class.java)
                    userList.add(user)
                }
                userAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Log.w("AdminFragment", "Error getting documents.", exception)
            }
    }
}

