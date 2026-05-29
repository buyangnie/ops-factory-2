import React, { useState } from 'react'

interface User {
  id: number
  name: string
  email: string
}

// TypeScript test file with some violations
export function UserList() {
  const [users, setUsers] = useState<any>([])
  const [loading, setLoading] = useState(false)

  async function fetchUsers() {
    setLoading(true)
    try {
      const response = await fetch('/api/users')
      const data = await response.json()
      setUsers(data)
    } catch (e) {
      console.log(e)
    }
    setLoading(false)
  }

  function handleClick(user: any) {
    // TODO: implement user details view
    console.log(user)
  }

  return (
    <div>
      {users.map((user: any, index: number) => (
        <div key={index} onClick={() => handleClick(user)}>
          {user.name}
        </div>
      ))}
    </div>
  )
}
